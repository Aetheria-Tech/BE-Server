# 02. `@Transactional`이 `Mono` 위에서 무효화된다

> 상태 · 대기
> 성격 · 동작 | 난이도 · 낮음 | 선행 항목 · [01번](01-dead-code.md)(대상 하나가 삭제로 사라짐)
> 함께 읽기 · [10번 — 포트가 리액티브 타입을 노출한다](10-reactive-types-in-ports.md). **리액티브 타입 자체는 문제가 아닙니다.**

## 1. 무엇이 문제인가

`@Transactional`이 붙은 메서드가 `Mono`를 반환하면, **그 애노테이션은 아무 일도 하지 않습니다.**
그런데 코드를 읽는 사람에게는 트랜잭션 경계가 있는 것처럼 보입니다.

저장소 전체에서 `@Transactional` 메서드는 14개이고, 그중 리액티브 타입을 반환하는 것은 둘입니다.

| 메서드 | 반환 | 비고 |
| --- | --- | --- |
| `WithdrawService.withdraw` | `Mono<Boolean>` | |
| `SocialTokenService.getFreshAccessToken` | `Mono<String>` | [01번](01-dead-code.md)에서 삭제되면 소멸 |

## 2. 근거

```bash
# @Transactional 메서드와 그 반환 타입을 모두 나열한다
python - <<'PY'
import re, glob, io
pat = re.compile(r'@Transactional[^\n]*\n(?:\s*(?:@\w+[^\n]*)\n)*\s*(?:public|protected)\s+([\w<>,\s\[\]?]+?)\s+(\w+)\s*\(', re.S)
for f in glob.glob("src/main/java/com/serverbe/**/*.java", recursive=True):
    s = io.open(f, encoding="utf-8").read()
    for m in pat.finditer(s):
        ret, name = m.group(1).strip(), m.group(2)
        if "Mono" in ret or "Flux" in ret:
            print(f.replace("\\","/").split("com/serverbe/")[-1], name, ret)
PY
```

## 3. 왜 무효한가

JPA가 쓰는 `PlatformTransactionManager`는 트랜잭션을 **호출 스레드에 바인딩**합니다
(`TransactionSynchronizationManager`가 `ThreadLocal`을 씁니다). 스프링의 선언적 트랜잭션 프록시는
**대상 메서드가 리턴할 때 커밋**합니다.

리액티브 메서드는 리턴할 때 **아무 일도 하지 않은 조립된 파이프라인**을 돌려줍니다. 실제 작업은
누군가 구독한 뒤, 그것도 `subscribeOn`/`publishOn`이 지정한 **다른 스레드**에서 일어납니다.

```
프록시가 트랜잭션 시작  →  메서드 실행: Mono 조립해서 리턴  →  프록시가 커밋
                                                                        ↓
                                        (한참 뒤, boundedElastic 스레드에서 실제 DB 작업)
                                         ← 여기엔 바인딩된 트랜잭션이 없다
```

즉 트랜잭션은 **DB를 한 번도 건드리지 않고 열렸다 닫힙니다.** 커넥션 획득 비용만 내고 보장은
하나도 사지 못합니다.

## 4. 지금 데이터가 깨지고 있나 — 아니오. 그래서 더 위험합니다

`WithdrawService.withdraw`는 실제 삭제를
[`UserDataCleanupManager.deleteAllUserData`](../../src/main/java/com/serverbe/application/service/helper/UserDataCleanupManager.java)에
맡기고, 그 메서드에는 **자기 몫의 `@Transactional`이 따로 붙어 있습니다.** 그래서 쓰기는 제대로
트랜잭션 안에서 일어납니다. 앞의 사용자 조회는 트랜잭션 밖에서 도는데, 단건 읽기라 문제가 드러나지
않습니다.

**문제는 지금이 아니라 다음입니다.** 누군가 "이미 `@Transactional`이 붙어 있으니 여기서 저장해도
되겠지" 하고 `map` 람다 안으로 저장 한 줄을 옮기는 순간, 그 저장은 **트랜잭션 없이, 아무 예외도
없이** 실행됩니다. 실패해도 롤백되지 않고, 로그도 남지 않습니다.

애노테이션이 아무 일도 하지 않는 것 자체보다, **"여기는 안전하다"고 말하고 있다는 것**이 결함입니다.

메서드의 javadoc도 그 오해를 거듭니다 — "**3. 트랜잭션 보장**: 자기 호출 문제를 방지하기 위해
`UserDataCleanupManager`를 통해 외부 호출 방식으로 데이터 파기를 수행합니다." 이 문장 자체는 맞지만,
바로 위에 붙은 `@Transactional`과 나란히 읽히면서 **경계가 이 메서드에 있다는 인상**을 만듭니다.

## 5. 어떻게 — 같은 저장소가 이미 정답을 알고 있다

새로 발명할 것이 없습니다. **옆 파일이 이미 올바른 패턴으로 돌고 있습니다.**

[`AiGenerationService`](../../src/main/java/com/serverbe/application/service/AiGenerationService.java)는
리액티브 사가 전체를 다루면서 선언적 트랜잭션을 한 번도 쓰지 않고, 트랜잭션이 필요한 지점마다
`TransactionTemplate`을 씁니다.

```java
return Mono.fromCallable(() -> transactionTemplate.execute(status -> taskUpdatePort.save(processingTask)))
        .subscribeOn(Schedulers.boundedElastic());
```

핵심은 **`Mono.fromCallable` 안쪽이 실제로 실행되는 스레드에서 트랜잭션이 열린다**는 것입니다.
조립 시점이 아니라 실행 시점에 열리므로 스레드가 어긋나지 않습니다.

[`AiResultRetrievalService`](../../src/main/java/com/serverbe/application/service/AiResultRetrievalService.java)의
클래스 javadoc은 그 선택의 이유까지 이미 적어 두었습니다 — "외부 네트워크 I/O 작업 시 DB 커넥션 풀
고갈을 방지하기 위해 의도적으로 선언적 트랜잭션을 배제하고".

따라서 고치는 방법은:

1. `WithdrawService.withdraw`에서 `@Transactional`을 **제거합니다.** 실제 쓰기는 이미
   `UserDataCleanupManager`가 자기 트랜잭션 안에서 하고 있으므로 동작은 바뀌지 않습니다.
2. javadoc의 "트랜잭션 보장" 항목을 **경계가 어디인지 명시하는 문장**으로 고칩니다 — 이 메서드가
   아니라 `deleteAllUserData`가 경계라는 사실을 적습니다.
3. `SocialTokenService`는 [01번](01-dead-code.md)에서 삭제됩니다. 만약 그 판단이 뒤집혀 살려 두게
   되면, 이쪽은 **읽기·외부호출·쓰기가 한 흐름에 섞여 있어** 실제로 위험합니다. 그때는 `map` 안의
   저장을 `TransactionTemplate`으로 감싸야 합니다.

## 6. 재발 방지

`LayerDependencyTest`에 규칙을 추가합니다.

```
트랜잭션_메서드는_리액티브_타입을_반환하지_않는다
  @Transactional 이 붙은 메서드를 가진 클래스는
  그 메서드의 반환 타입이 Mono / Flux 여서는 안 된다
```

기존 테스트에 이미 **메서드 애노테이션을 보는 커스텀 술어**(`haveMethodAnnotatedWithAnyOf`)가
있으므로 그 틀을 재사용할 수 있습니다. 다만 이번에는 애노테이션과 반환 타입을 **함께** 봐야 하므로
`JavaMethod` 단위로 도는 별도 술어가 필요합니다.

규칙 이름을 한글로 두는 것은 이 테스트의 기존 관례입니다 — 실패 메시지가 스스로 무엇을 어겼는지
설명하게 하기 위해서입니다.

## 7. 하지 않기로 한 것

- **리액티브 타입을 걷어내지 않습니다.** 문제는 `Mono`가 아니라 거기에 스레드 바인딩 트랜잭션을
  얹은 것입니다. 근거는 [10번 문서](10-reactive-types-in-ports.md)에 따로 적었습니다.
- **`R2DBC`나 리액티브 트랜잭션 매니저로 가지 않습니다.** 이 서버의 영속성은 JPA이고, 리액티브는
  외부 API 호출 경계에만 있습니다. 트랜잭션이 필요한 곳은 이미 `boundedElastic`에서 블로킹으로
  돌고 있으므로 `TransactionTemplate`이면 충분합니다. 데이터 접근 전체를 리액티브로 바꾸는 것은
  이 항목이 감당할 범위가 아닙니다.

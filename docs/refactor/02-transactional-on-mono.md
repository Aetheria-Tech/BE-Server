# 02. `@Transactional`이 `Mono` 위에서 무효화된다

> 상태 · **완료** (커밋 전, 워킹 트리)
> 성격 · 동작 | 난이도 · 낮음 | 선행 항목 · [01번](01-dead-code.md) — **완료됨.** 대상 하나가 삭제로 사라져 **남은 대상은 `WithdrawService` 하나**였습니다
> 함께 읽기 · [10번 — 포트가 리액티브 타입을 노출한다](10-reactive-types-in-ports.md). **리액티브 타입 자체는 문제가 아닙니다.**
> **이 백로그에서 유일하게 동작이 틀렸던 항목입니다.** 고친 뒤 같은 실수가 다시 들어오지 못하도록 ArchUnit 규칙을 함께 세웠습니다 — 6절.

## 1. 무엇이 문제인가

`@Transactional`이 붙은 메서드가 `Mono`를 반환하면, **그 애노테이션은 아무 일도 하지 않습니다.**
그런데 코드를 읽는 사람에게는 트랜잭션 경계가 있는 것처럼 보입니다.

저장소 전체에서 `@Transactional` 메서드는 13개였고, 그중 리액티브 타입을 반환하는 것은 **하나**였습니다.

| 메서드 | 반환 | 비고 |
| --- | --- | --- |
| `WithdrawService.withdraw` | `Mono<Boolean>` | |

원래 둘이었습니다. `SocialTokenService.getFreshAccessToken`이 같은 문제를 안고 있었고 그쪽이 훨씬
위험했지만(읽기·외부호출·쓰기가 한 흐름에 섞여 있었습니다), **[01번](01-dead-code.md)에서 죽은
코드로 삭제되면서 함께 사라졌습니다.** 남은 하나가 이 문서의 대상입니다.

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

조사 중에 한 가지가 더 나왔습니다. 그 "단건 읽기"는 사실 **쓰기를 할 수도 있습니다** —
[`UserPersistenceAdapter.mapToDomainWithMigration`](../../src/main/java/com/serverbe/adapter/out/persistence/user/UserPersistenceAdapter.java)은
암호화 버전이 낮은 엔티티를 만나면 `saveAndFlush`로 다시 암호화해 저장합니다. 이것도 깨지지 않는데,
이유가 중요합니다 — **Spring Data의 `saveAndFlush`가 자기 트랜잭션을 갖고 있기 때문**이지
`withdraw`에 붙은 `@Transactional`이 지켜 주기 때문이 아닙니다. 이 메서드의 애노테이션은 그 쓰기가
일어나는 스레드에 존재한 적이 없습니다. **문서의 논지가 오히려 한 겹 더 선명해집니다.**

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

따라서 한 것은:

1. `WithdrawService.withdraw`에서 `@Transactional`을 **제거했습니다.** 실제 쓰기는 이미
   `UserDataCleanupManager`가 자기 트랜잭션 안에서 하고 있으므로 동작은 바뀌지 않습니다. import도
   함께 지웠습니다.
2. javadoc의 "트랜잭션 보장" 항목을 **"트랜잭션 경계"**로 바꿔, 경계가 이 메서드가 아니라
   `deleteAllUserData`에 있다는 사실을 적었습니다. `@implNote`에는 **왜 여기 붙일 수 없는지**와,
   이 흐름에서 트랜잭션이 필요해지면 `TransactionTemplate`을 쓰라는 안내를 남겼습니다. 다음 사람이
   애노테이션을 되돌리는 것이 이 문서의 유일한 실패 시나리오이므로, 반례를 코드 옆에 두었습니다.
3. 본문의 `// 2. 내부 데이터 파기 (트랜잭션 보장)` 주석도 같은 오해를 만들고 있어
   `(트랜잭션 경계는 이 헬퍼가 연다)`로 고쳤습니다.

**동작이 바뀌지 않는다는 것은 기존 테스트가 말해 줍니다** — `WithdrawServiceTest`의 네 케이스를
한 줄도 고치지 않고 통과시켰습니다.

원래 있던 두 번째 대상(`SocialTokenService`)은 [01번](01-dead-code.md)에서 삭제되어 고칠 것이
없었습니다.

## 6. 재발 방지

[`LayerDependencyTest`](../../src/test/java/com/serverbe/architecture/LayerDependencyTest.java)에
규칙을 세웠습니다.

```java
static final ArchRule 트랜잭션_메서드는_리액티브_타입을_반환하지_않는다 = noMethods()
        .that().areAnnotatedWith(Transactional.class)
        .or().areDeclaredInClassesThat().areAnnotatedWith(Transactional.class)
        .should().haveRawReturnType(beReactiveType());
```

**계획과 달라진 곳이 있습니다.** 처음에는 기존 `haveMethodAnnotatedWithAnyOf`를 본떠 `JavaMethod`
단위 술어를 새로 만들 생각이었는데, ArchUnit의 `noMethods()`가 이미 **메서드 단위로** 돌고
`haveRawReturnType`이 술어를 받으므로 그럴 필요가 없었습니다. 덤으로 `.or().areDeclaredInClassesThat()`
한 줄이 **클래스 레벨 `@Transactional`까지** 덮습니다 — 지금 저장소에는 없지만, 나중에 누가 클래스에
붙이면 그때가 이 규칙이 가장 필요한 순간입니다.

반환 타입은 **이름으로** 비교합니다. `@AnalyzeClasses`가 `DoNotIncludeJars`라 Reactor 타입은
스텁으로 들어오고, `isAssignableTo(Publisher.class)` 같은 계층 질의는 풀리지 않을 수 있습니다.

규칙 이름을 한글로 두는 것은 이 테스트의 기존 관례입니다 — 실패 메시지가 스스로 무엇을 어겼는지
설명하게 하기 위해서입니다.

### 규칙이 정말 잡는지 확인했습니다

**항상 통과하는 규칙은 규칙이 아닙니다.** `@Transactional`을 `withdraw`에 잠깐 되돌려 놓고 돌려
실패하는 것을 보고, 다시 지웠습니다.

```
LayerDependencyTest > 트랜잭션_메서드는_리액티브_타입을_반환하지_않는다 FAILED
6 tests completed, 1 failed
```

## 7. 하지 않기로 한 것

- **리액티브 타입을 걷어내지 않습니다.** 문제는 `Mono`가 아니라 거기에 스레드 바인딩 트랜잭션을
  얹은 것입니다. 근거는 [10번 문서](10-reactive-types-in-ports.md)에 따로 적었습니다.
- **`R2DBC`나 리액티브 트랜잭션 매니저로 가지 않습니다.** 이 서버의 영속성은 JPA이고, 리액티브는
  외부 API 호출 경계에만 있습니다. 트랜잭션이 필요한 곳은 이미 `boundedElastic`에서 블로킹으로
  돌고 있으므로 `TransactionTemplate`이면 충분합니다. 데이터 접근 전체를 리액티브로 바꾸는 것은
  이 항목이 감당할 범위가 아닙니다.

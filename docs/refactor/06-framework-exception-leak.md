# 06. 애플리케이션이 스프링 예외를 잡는다

> 상태 · **완료**
> 성격 · 경계 | 난이도 · 낮음 | 선행 항목 · 없음
> 이 항목의 절반은 **왜 지금까지 잡히지 않았는가**였습니다. 그리고 **착수 전에 세운 규칙도
> 이걸 잡지 못했습니다** — 4절이 이 문서의 진짜 내용입니다.

## 1. 무엇이 문제였는가

[`UserDataSyncManager`](../../src/main/java/com/serverbe/application/service/helper/UserDataSyncManager.java)가
스프링의 `org.springframework.dao.DataIntegrityViolationException`을 import하고 catch했습니다.

애플리케이션 계층은 프레임워크를 몰라야 하고, 도메인에는 이미 같은 뜻의 예외가 있습니다 —
`com.serverbe.domain.exception.server.DataIntegrityViolationException`. **이름이 같은 두 예외가
서로 다른 계층에서 공존했습니다.**

## 2. 근거

```bash
grep -rn "org.springframework.dao" src/main/java/
```

네 줄이 나왔습니다. 셋은 `adapter/out/persistence/...`이라 정상이고, **나머지 하나가
`application/service/helper/UserDataSyncManager.java`** 였습니다.

```java
// UserDataSyncManager#registerOrRecover — 애플리케이션 계층인데 스프링 예외를 잡았다
} catch (DataIntegrityViolationException e) {
```

여기서 잡는 것이 **스프링 것**이라는 사실은 파일 머리의 import를 봐야만 알 수 있었습니다.

## 3. 왜 지금까지 잡히지 않았나 — 규칙의 사각지대

`LayerDependencyTest`의 애플리케이션 규칙은 **차단 목록**이었습니다.

```java
애플리케이션은_어댑터와_인프라를_모른다 = noClasses()
        .that().resideInAPackage("com.serverbe.application..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.serverbe.adapter..", "com.serverbe.infrastructure..");
```

**막는 대상이 우리 패키지 두 개뿐입니다.** `org.springframework..`는 목록에 없으므로 통과합니다.
바로 위의 도메인 규칙은 정반대로 **허용 목록**이라, 도메인은 새 프레임워크가 들어와도 막힙니다.

**규칙이 덮지 않는 경계는 반드시 샙니다.** 이 저장소가 `LayerDependencyTest`를 도입한 이유가
여기서 절반만 실현되어 있었습니다.

## 4. 예상 못 한 것 — 허용 목록 규칙도 이걸 잡지 못했습니다

**이 항목에서 실제로 배운 것은 이겁니다.**

착수 전 계획은 "허용 목록 규칙을 켜는 순간 이 위반이 잡힌다"였습니다. 04·05번이 규칙을 확인하려고
고친 것을 일부러 되돌려야 했던 것과 달리, 이번엔 **위반이 아직 살아 있으니 규칙만 켜면 빨간
줄을 볼 수 있다**고 봤습니다. 그래서 규칙을 먼저 넣고 돌렸습니다.

```
BUILD SUCCESSFUL
9 tests completed, 0 failed
```

**통과했습니다.** 위반은 그대로 있는데 규칙이 조용했습니다.

### 왜인가

ArchUnit이 의존으로 세는 것은 필드 타입·파라미터·반환 타입·메서드 호출·애노테이션 같은
것들입니다. **잡는 예외의 타입은 그중에 없습니다.** 바이트코드에서 catch 대상은 예외 테이블에만
적히고, 지역 변수 타입은 디버그 정보일 뿐이라 `dependOnClassesThat`의 시야 밖입니다.

`UserDataSyncManager`의 의존 목록을 직접 찍어 확인했습니다.

```
### DIRECT DEPENDENCIES FROM SELF ###
org.slf4j.Logger                                        [Field ... has type ...]
org.springframework.stereotype.Component                [Class ... is annotated with ...]
org.springframework.transaction.PlatformTransactionManager  [Constructor ... has parameter of type ...]
org.springframework.transaction.support.TransactionTemplate [Field ... has type ...]
...
### TRY-CATCH BLOCKS ###
registerOrRecover catches [org.springframework.dao.DataIntegrityViolationException]   ← 의존 목록엔 없다
```

**규칙의 사각지대를 메우려고 만든 규칙에 같은 종류의 사각지대가 있었습니다.** 3절이 진단한
것과 정확히 같은 실수를 한 번 더 한 셈이고, **실제로 돌려 보지 않았다면 "규칙을 세웠다"고
믿으며 넘어갔을 것입니다.** 04번에서 "항상 통과하는 규칙은 규칙이 아니다"라며 일부러 깨뜨려
본 절차가, 여기서는 계획 자체를 구해 냈습니다.

### 그래서 규칙이 둘입니다

하나는 **쓰는 타입**을, 하나는 **잡는 타입**을 봅니다. 후자는 `getTryCatchBlocks()`로 직접
훑어야 하므로 커스텀 `ArchCondition`이 필요합니다(5절).

## 5. 어떻게 — 한 일

### 번역을 어댑터로 내렸습니다

[`UserPersistenceAdapter.save`](../../src/main/java/com/serverbe/adapter/out/persistence/user/UserPersistenceAdapter.java)가
스프링 예외를 잡아 도메인 예외로 바꿔 던집니다. **본보기가 옆에 있었습니다** —
`AiTaskPersistenceAdapter`가 이미 같은 일을 하고 있었고, 그 관용구(스프링 예외를 import하지 않고
catch 절에 패키지 전체 이름을 쓰기)를 그대로 따랐습니다.

```java
} catch (org.springframework.dao.DataIntegrityViolationException e) {
    // 어댑터는 "DB가 무결성 이유로 거부했다"까지만 말합니다. 그것이 동시 최초 로그인 경합인지는
    // 재조회에 성공해 봐야 알 수 있고, 그 판단은 호출자(UserDataSyncManager)의 몫입니다.
    log.warn("[Integrity] 사용자 저장이 DB 무결성 제약에서 거부되었습니다.", e);
    throw new DataIntegrityViolationException(
            ServerErrorCode.INTERNAL_SERVER_ERROR, e.getMostSpecificCause().getMessage());
}
```

**착수 전에 확인한 전제가 하나 있었습니다** — `UserEntity`의 식별자가
`@GeneratedValue(strategy = IDENTITY)`라 `persist` 시점에 INSERT가 즉시 실행됩니다. 그래서 제약
위반이 커밋까지 미뤄지지 않고 **`save()` 안에서** 잡힙니다. 지연 flush였다면 예외는
`TransactionTemplate`이 커밋할 때, 즉 어댑터 바깥에서 터졌을 것이고 **이 계획 자체가 성립하지
않았습니다.**

`ErrorCode`로 `INTERNAL_SERVER_ERROR`를 골랐습니다. 착수 전에도 복구 실패 시 스프링 예외가
`handleException(Exception)`으로 떨어져 500(COMMON_003)이 나갔고,
`BusinessExceptionHandler`의 도메인 핸들러도 `errorCode`를 무시하고 항상 500을 냅니다.
**응답이 한 글자도 바뀌지 않습니다** — 이 항목은 경계 리팩터링이지 응답 변경이 아닙니다.
"경합"이라는 해석은 어댑터가 내릴 수 있는 것이 아니어서 `ASYNC_RACE_CONDITION`(409)은 쓰지
않았습니다. 어댑터는 어떤 제약이 깨졌는지 모릅니다.

### 애플리케이션은 도메인 예외만 잡습니다

`UserDataSyncManager`는 import 한 줄이 바뀌었고 **복구 로직은 그대로**입니다. 재조회 후 기존
회원으로 이어 가는 판단은 애플리케이션의 몫이고 이 항목이 바꾸려던 것이 아닙니다.
`registerOrRecover`의 Javadoc에 **어디서 번역되는지**를 적었습니다 — 코드가 말하지 않는 사실을
주석이 말해야 합니다.

### 테스트가 두 층으로 갈렸습니다

문서가 요구한 선명함이 이것입니다.

- `UserPersistenceAdapterTest` — **번역이 일어나는가.** `save` 경로 테스트가 아예 없던 파일이라
  둘을 새로 넣었습니다(제약 위반 번역, 정상 저장 통과)
- `UserDataSyncManagerTest` — **번역된 예외를 받으면 복구하는가.** stub이 던지는 타입만 도메인
  것으로 바뀌었고 시나리오는 그대로입니다

도메인 클래스에는 String 단일 생성자가 없어(1-arg 생성자가 `protected`) stub이
`new DataIntegrityViolationException(ServerErrorCode.INTERNAL_SERVER_ERROR, "...")`로 바뀌었습니다.
`hasMessageContaining`은 그대로 통과합니다 — `BusinessException(ErrorCode, String)`이
`super(message)`를 호출하기 때문입니다.

### 확인한 것

- **`gradlew build` 통과**(전체 재실행). 두 테스트 모두 순수 Mockito라 기본 `test` 태스크에서 돕니다
- **누수가 사라졌습니다.** `org.springframework.dao`는 이제 `adapter/out/persistence/` 네 줄뿐입니다
- **애플리케이션에 남은 스프링은 `stereotype`과 `transaction`뿐입니다** — 정확히 허용 목록 그대로

## 6. 재발 방지 — 규칙 둘

### 하나 — 허용 목록

`애플리케이션은_어댑터와_인프라를_모른다`는 **남겼습니다.** 새 규칙이 포함하지만, 어댑터나
인프라를 import했을 때 이름부터 그것을 말하는 규칙이 함께 터져야 원인이 바로 읽힙니다.

```java
static final ArchRule 애플리케이션은_포트와_도메인_안에서만_논다 = noClasses()
        .that().resideInAPackage("com.serverbe.application..")
        .should().dependOnClassesThat()
        .resideOutsideOfPackages(
                "com.serverbe.application..",
                "com.serverbe.domain..",
                "java..",
                "lombok..",                          // @Slf4j·@RequiredArgsConstructor 와 lombok.Generated
                "org.slf4j..",                       // @Slf4j 가 만드는 Logger 필드. import 없이 들어온다
                "org.springframework.stereotype..",  // @Service·@Component — 빈 선언
                "org.springframework.transaction..", // 트랜잭션 경계를 정하는 곳이 이 계층이다
                "reactor.."                          // 10번 문서에서 남기기로 결정한 것
        );
```

**허용 목록은 import가 아니라 ArchUnit이 실제로 보는 의존을 세어서 정했습니다.** 애플리케이션
92개 클래스의 의존을 전부 찍어 보니 **import 한 줄 없이 들어오는 것들**이 있었습니다.

| import 없이 들어오는 것 | 경로 |
| --- | --- |
| `org.slf4j.Logger` · `LoggerFactory` | Lombok `@Slf4j`가 만드는 필드와 static 초기화 |
| `reactor.util.function.Tuple2` | `AiGenerationService`의 `zipWhen` |
| `transaction.annotation.Isolation` · `Propagation` | `@Transactional`의 애노테이션 멤버 타입 |

둘째 것 때문에 `reactor.core..`가 아니라 **`reactor..`** 로 잡아야 합니다. 소스의 import만 보고
목록을 짰다면 규칙이 첫 실행에서 깨졌을 것입니다.

**목록에 `org.springframework.dao`는 없습니다.** 그것이 요점입니다. 그리고 목록에 무엇을 넣느냐가
곧 선언입니다 — 트랜잭션과 Reactor를 허용한다는 것은 그 둘이 이 계층의 어휘라는 뜻이고,
**목록 자체가 "애플리케이션이 프레임워크에 얼마나 묶여 있는가"를 한 화면에 드러냅니다.**

### 둘 — 잡는 예외

```java
static final ArchRule 애플리케이션은_프레임워크_예외를_잡지_않는다 = noClasses()
        .that().resideInAPackage("com.serverbe.application..")
        .should(catchThrowablesOutsideOf(
                "com.serverbe.application..", "com.serverbe.domain..", "java.."));
```

`java..`를 허용하는 이유는 애플리케이션이 실제로 `Exception`을 잡기 때문입니다(비동기 흐름의
최후 방어선 여섯 곳). 도메인 예외도 잡습니다(`AiNotificationService`의
`AsyncRaceConditionException`). **프레임워크 예외만 잡던 곳이 정확히 하나였습니다.**

### 규칙이 정말 잡는지 확인했습니다

이번엔 되돌릴 필요가 없었습니다. 고치기 **전에** 규칙을 켜서 실패를 봤습니다.

```
LayerDependencyTest > 애플리케이션은_프레임워크_예외를_잡지_않는다 FAILED
10 tests completed, 1 failed

Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in a package
'com.serverbe.application..' should 허용된 패키지 밖의 예외를 catch 한다' was violated (1 times):
Class <com.serverbe.application.service.helper.UserDataSyncManager> catches
<org.springframework.dao.DataIntegrityViolationException> in (UserDataSyncManager.java:64)
```

고친 뒤 10개 전부 통과합니다.

## 7. 하지 않기로 한 것

- **`@Transactional`과 Reactor를 걷어내지 않았습니다.** 허용 목록에 넣었습니다. 전자는 이 계층이
  트랜잭션 경계를 정하는 곳이기 때문이고, 후자의 근거는 [10번 문서](10-reactive-types-in-ports.md)에
  있습니다.
- **도메인 예외의 이름을 바꾸지 않았습니다.** 애플리케이션에서 스프링 쪽이 사라졌으므로 겹칠 일도
  사라졌습니다. 어댑터에는 여전히 둘 다 보이지만, 거기서 FQN을 쓰는 것은 이미 확립된 관용구입니다.
- **복구 로직을 바꾸지 않았습니다.** 재조회 후 기존 회원으로 이어 가는 판단은 그대로입니다.
- **`AiTaskPersistenceAdapter`를 손대지 않았습니다.** 이미 올바른 자리에서 번역하고 있습니다.
- **어댑터의 `DataAccessException` import는 그대로입니다.** 어댑터가 스프링 예외를 아는 것은
  문제가 아니라 어댑터의 일입니다.

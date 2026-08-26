# 06. 애플리케이션이 스프링 예외를 잡는다

> 상태 · 대기
> 성격 · 경계 | 난이도 · 낮음 | 선행 항목 · 없음
> 이 항목의 절반은 **왜 지금까지 잡히지 않았는가**입니다. ArchUnit 규칙에 사각지대가 있습니다.

## 1. 무엇이 문제인가

[`UserDataSyncManager`](../../src/main/java/com/serverbe/application/service/helper/UserDataSyncManager.java)가
스프링의 `org.springframework.dao.DataIntegrityViolationException`을 import하고 catch합니다.

애플리케이션 계층은 프레임워크를 몰라야 하고, 도메인에는 이미 같은 뜻의 예외가 있습니다 —
`com.serverbe.domain.exception.server.DataIntegrityViolationException`. **이름이 같은 두 예외가
서로 다른 계층에서 공존합니다.**

## 2. 근거

```bash
grep -rn "org.springframework.dao" src/main/java/
```

네 줄이 나옵니다. 셋은 `adapter/out/persistence/...`이라 정상이고, **나머지 하나가
`application/service/helper/UserDataSyncManager.java`** 입니다.

```java
// UserDataSyncManager#registerOrRecover — 애플리케이션 계층인데 스프링 예외를 잡는다
} catch (DataIntegrityViolationException e) {
    log.warn("[REGISTER] 동시 최초 로그인 경합 감지, 기존 회원으로 복구합니다: Provider={}", oauthInfo.provider());

    return userRepositoryPort.findByOauthId(oauthInfo.oauthId(), oauthInfo.provider())
            .map(existingUser -> refresh(existingUser, oauthInfo))
            .orElseThrow(() -> e);
}
```

여기서 잡는 `DataIntegrityViolationException`은 **스프링 것**입니다. 파일 머리의 import를 봐야만
알 수 있습니다.

## 3. 왜 지금까지 잡히지 않았나 — 규칙의 사각지대

`LayerDependencyTest`의 규칙은 이렇게 되어 있습니다.

```java
애플리케이션은_어댑터와_인프라를_모른다 = noClasses()
        .that().resideInAPackage("com.serverbe.application..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.serverbe.adapter..", "com.serverbe.infrastructure..");
```

**막는 대상이 우리 패키지 두 개뿐입니다.** `org.springframework..`는 목록에 없으므로 통과합니다.

바로 위에 있는 도메인 규칙은 정반대 방식으로 쓰여 있습니다.

```java
도메인은_JDK와_Lombok에만_의존한다 = noClasses()
        .that().resideInAPackage("com.serverbe.domain..")
        .should().dependOnClassesThat()
        .resideOutsideOfPackages("com.serverbe.domain..", "java..", "lombok..");
```

이쪽은 **허용 목록**입니다. 그래서 도메인은 새 프레임워크가 들어와도 막히지만, **애플리케이션은
차단 목록이라 목록에 없는 것이 전부 통과합니다.**

이것이 이 문서의 핵심입니다 — **규칙이 덮지 않는 경계는 반드시 샙니다.** 이 저장소가
`LayerDependencyTest`를 도입한 이유("규약이 아니라 테스트가 경계를 지키게 한다")가 여기서 절반만
실현되어 있었습니다.

## 4. 왜 고쳐야 하는가

두 가지입니다.

**첫째, 번역이 일어나야 할 곳에서 일어나지 않았습니다.** 유니크 제약 위반을 도메인 언어로 옮기는
일은 영속성 어댑터의 몫입니다. 실제로 옆에 있는
[`AiTaskPersistenceAdapter`](../../src/main/java/com/serverbe/adapter/out/persistence/task/AiTaskPersistenceAdapter.java)는
그 일을 하고 있습니다 — 같은 스프링 예외를 어댑터 안에서 잡습니다. **같은 저장소 안에서 두 방식이
공존합니다.**

**둘째, 이름이 같은 두 예외가 있습니다.** `UserDataSyncManager`를 읽는 사람은 import를 확인하기
전까지 어느 쪽인지 알 수 없고, IDE 자동 import는 둘 중 아무거나 고릅니다. 도메인 예외를 던져야 할
자리에 스프링 예외가 들어가도 컴파일은 통과합니다.

이 마찰은 이미 코드에 흔적을 남겼습니다. `AiTaskPersistenceAdapter`는 catch 절에 **패키지 전체
이름을 그대로 씁니다.**

```java
} catch (org.springframework.dao.DataIntegrityViolationException e) {
    // active_user_id 유니크 제약 위반 = 이 사용자에게 이미 진행 중인 작업이 있다는 뜻입니다.
    log.warn("[Concurrency] 유저 {} 의 동시 AI 작업 생성이 DB 유니크 제약에서 차단되었습니다.", aiTask.userId(), e);
    throw new AiException(AiErrorCode.DUPLICATE_AI_REQUEST);
}
```

짧게 쓸 수 있는데 길게 쓴 이유는 하나뿐입니다 — **짧게 쓰면 어느 예외인지 알 수 없기 때문입니다.**
이름 충돌이 실제로 코드를 불편하게 만들고 있다는 증거입니다.

## 5. 어떻게

1. **번역을 어댑터로 내립니다.** `UserPersistenceAdapter.save`가 스프링의
   `DataIntegrityViolationException`을 잡아 도메인
   `DataIntegrityViolationException`(또는 경합 상황에 더 맞는 도메인 예외)으로 바꿔 던집니다.
   `AiTaskPersistenceAdapter`가 이미 하고 있는 것과 같은 모양이므로 **본보기가 옆에 있습니다.**
2. `UserDataSyncManager`는 도메인 예외만 잡습니다. import 한 줄이 사라집니다.
3. 복구 로직(재조회 후 기존 회원으로 이어 가기)은 **그대로 둡니다.** 그건 애플리케이션의 판단이고,
   이 항목이 바꾸려는 것이 아닙니다.

**주의** — 던지는 예외 종류가 바뀌면 이 경합 시나리오를 덮는 테스트가 함께 움직여야 합니다.
`UserDataSyncManagerTest`와 `UserPersistenceAdapterTest`가 모두 있으므로, **어느 쪽이 무엇을
검증하는지가 이 리팩터링으로 더 선명해져야 합니다** — 어댑터 테스트가 "번역이 일어나는가", 매니저
테스트가 "번역된 예외를 받으면 복구하는가"를 맡습니다.

## 6. 재발 방지

`LayerDependencyTest`의 애플리케이션 규칙을 **차단 목록에서 허용 목록으로** 바꿉니다. 도메인
규칙과 같은 형태가 됩니다.

```
애플리케이션은_포트와_도메인_안에서만_논다
  application.. 의 클래스는
  application.. / domain.. / java.. / lombok.. / (허용한 몇 가지) 밖에 의존하지 않는다
```

**허용해야 할 것을 미리 정합니다.** 지금 애플리케이션 계층이 실제로 쓰는 프레임워크 타입은 조사로
확인되어 있습니다.

- `org.springframework.stereotype.Service` / `Component` — 빈 선언
- `org.springframework.transaction..` — `@Transactional`, `TransactionTemplate`,
  `TransactionSynchronization`
- `reactor.core..` — [10번 문서](10-reactive-types-in-ports.md)에서 남기기로 결정한 것
- `org.slf4j..`

이 목록에 **`org.springframework.dao`는 없습니다.** 그래서 규칙을 켜는 순간 이 항목이 잡히고,
고친 뒤에는 새로 새는 것도 잡힙니다.

허용 목록을 정하는 일 자체가 **"애플리케이션이 프레임워크에 얼마나 묶여 있는가"를 한 화면에
드러내는 문서**가 됩니다. 트랜잭션과 Reactor를 허용한다는 것은 그 둘이 이 계층의 어휘라는 선언이고,
그 선언은 명시적일수록 좋습니다.

## 7. 하지 않기로 한 것

- **`@Transactional`과 Reactor를 걷어내지 않습니다.** 허용 목록에 넣습니다. 전자는 이 계층이
  트랜잭션 경계를 정하는 곳이기 때문이고, 후자의 근거는 [10번 문서](10-reactive-types-in-ports.md)에
  있습니다.
- **도메인 예외의 이름을 바꾸지 않습니다.** `DataIntegrityViolationException`이라는 이름이 스프링
  것과 겹치는 게 혼란의 원인 중 하나지만, 애플리케이션에서 스프링 쪽이 사라지면 겹칠 일도
  사라집니다. 이름을 바꾸는 것은 더 큰 변경이고 얻는 게 적습니다.

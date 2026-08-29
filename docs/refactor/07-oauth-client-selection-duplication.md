# 07. OAuth 클라이언트 선택이 두 벌 중복

> 상태 · **완료**
> 성격 · 중복 | 난이도 · 낮음 | 선행 항목 · [01번](01-dead-code.md) — 완료됨. 세 벌 중 하나가 삭제로 사라져 **남은 것은 두 벌**이었습니다
> **제목이 이 항목을 잘못 부르고 있었습니다** — 고친 것은 중복이 아니라 **선택 그 자체**입니다. 3절을 보세요.

## 1. 무엇이 문제였는가

`OAuthProvider`로 알맞은 `OAuthClientPort` 구현체를 고르는 메서드가 **두 서비스에 그대로 두 번**
있었습니다.

```java
private OAuthClientPort getClient(OAuthProvider provider) {
    return oAuthClients.stream()
            .filter(client -> client.supports(provider))
            .findFirst()
            .orElseThrow(() -> new AuthException(AuthErrorCode.UNSUPPORTED_SOCIAL_LOGIN));
}
```

- [`LoginService`](../../src/main/java/com/serverbe/application/service/LoginService.java) (호출 2곳)
- [`WithdrawService`](../../src/main/java/com/serverbe/application/service/WithdrawService.java) (호출 1곳)

원래 세 벌이었습니다. 세 번째는 `SocialTokenService`에 있었고
[01번](01-dead-code.md)에서 파일째 삭제되면서 함께 사라졌습니다.

## 2. 근거

```bash
grep -rn "private OAuthClientPort getClient" src/main/java --include=*.java
```

두 서비스 모두 `private final List<OAuthClientPort> oAuthClients;`를 주입받고, 쓸 때마다
**리스트를 순회해 매번 다시 골랐습니다.**

## 3. 왜 고쳤는가 — 중복은 이유가 아니었습니다

중복 자체는 크지 않았습니다. 다섯 줄짜리이고, 01번을 닫은 뒤로는 두 곳뿐이었습니다.
**두 곳의 중복만으로 리팩터링을 정당화하기는 약합니다.**

진짜 이유는 다른 데 있었습니다. 그 구조는 **매 호출마다 "이 provider를 누가 지원하는지"를 다시
물었습니다.** 그 답은 애플리케이션 기동 시점에 이미 정해져 있고 절대 바뀌지 않습니다. 즉 런타임에
반복할 이유가 없는 탐색이고, 그 탐색을 위해 `OAuthClientPort`가 `supports(provider)`라는
**자기 소개 메서드**를 계약에 달고 있었습니다.

포트 인터페이스에 `supports()`가 있다는 것은 **"나를 고르는 방법은 호출자가 알아서 하라"** 는
뜻입니다. 어댑터가 자기를 어떻게 선택할지까지 계약에 적을 필요는 없습니다.

## 4. 어떻게 — 한 일

문서가 권한 **갈래 B(선택 자체를 없앤다)** 를 택했습니다. 갈래 A(공통 헬퍼로 뽑기)는 중복만
없애고 **탐색과 `supports()`를 그대로 남기므로** 원인을 건드리지 못합니다.

### 질의를 선언으로 바꿨습니다 — 이것이 핵심입니다

```java
// 이전 — 질의. 호출자가 후보를 하나씩 들고 물어봐야 한다
boolean supports(OAuthProvider provider);

// 지금 — 선언. 그 자체가 디스패치 키가 된다
OAuthProvider provider();
```

**`supports(provider)`와 `provider()`의 차이가 이 항목 전체를 결정했습니다.** 전자로는 Map의 키를
만들 수 없어 순회가 강제되고, 후자면 조립이 기동 시점에 한 번으로 끝납니다.

교체는 쉬웠습니다. 두 구현체 모두 `return provider == OAuthProvider.KAKAO;` 같은 **단일 비교**여서
`return OAuthProvider.KAKAO;`가 되었을 뿐입니다. **04번에서 배운 것과 같은 신호입니다** — 바꾸기
쉬웠다는 사실 자체가 "원래 그 모양이었어야 했다"는 진단입니다.

### 조립은 인프라가 합니다

[`infrastructure/config/OAuthClientConfig`](../../src/main/java/com/serverbe/infrastructure/config/OAuthClientConfig.java)
가 `Map<OAuthProvider, OAuthClientPort>`를 만들어 넘기고, 두 서비스는 그 Map을 주입받습니다.

**이 클래스가 애플리케이션이 아니라 인프라에 있는 것은 06번의 결과입니다.**
`@Configuration`·`@Bean`은 스프링 타입이고, 06번에서 켠
`애플리케이션은_포트와_도메인_안에서만_논다`가 애플리케이션 계층에서 그것들을 금지합니다.
`ApplicationPolicyConfig`가 이미 같은 모양(**인프라가 조립하고 애플리케이션은 결과만 받는다**)이라
선례를 그대로 따랐습니다.

### 조용한 승부를 기동 실패로 바꿨습니다 — 예상 못 한 수확

```java
return clients.stream().collect(Collectors.toUnmodifiableMap(
        OAuthClientPort::provider,
        Function.identity(),
        (first, second) -> { throw new IllegalStateException(...); }));
```

문서는 이걸 "할 수 있다"고만 적어 뒀는데, 실제로 해 보니 **이 세 줄이 이 항목에서 가장 값어치
있는 부분**이었습니다. 이전 방식에서는 같은 provider를 두 어댑터가 지원해도 아무 일도 일어나지
않았습니다 — `findFirst()`가 조용히 하나를 골랐고, **어느 쪽이 이길지는 빈 등록 순서에 달려 있어
예측할 수 없었습니다.** 신호를 전혀 남기지 않는 종류의 버그입니다. 이제는 어느 두 클래스가
충돌했는지와 함께 기동이 실패합니다.

### 서비스 쪽 — 무엇이 사라지고 무엇이 남는가

```java
private OAuthClientPort getClient(OAuthProvider provider) {
    OAuthClientPort client = oAuthClients.get(provider);
    if (client == null) { log.warn(...); throw new AuthException(AuthErrorCode.UNSUPPORTED_SOCIAL_LOGIN); }
    return client;
}
```

**정직하게 적어 둡니다 — 두 서비스에 여전히 비슷한 네 줄이 있습니다.** 사라진 것은 **탐색**과
포트의 `supports()`이고, 남은 것은 "없으면 던진다"입니다. 두 서비스가 서로 다른 로그 메시지를
쓰므로(`SECURITY ALERT` / `SECURITY/CONFIG ERROR`) 원래도 완전히 같은 코드는 아니었습니다.
**3절이 "중복은 이 항목의 이유가 아니다"라고 못박아 둔 것과 일치하는 결과입니다** — 이유였던 것은
사라졌고, 이유가 아니었던 것은 조금 남았습니다.

`AuthErrorCode.UNSUPPORTED_SOCIAL_LOGIN`은 그대로입니다. Map으로 바꿔도 `null`이 나오는 경우는
남기 때문입니다(문서 6절).

### 테스트가 정직해졌습니다

이건 계획에 없던 부수 효과입니다. 이전 `WithdrawServiceTest`의 "미지원 provider" 시나리오는
이랬습니다.

```java
given(kakaoClient.supports(OAuthProvider.KAKAO)).willReturn(false);   // 카카오가 카카오를 부정한다
```

**실제로는 일어날 수 없는 상황을 스텁으로 만들어 낸 설정**이었습니다. 리스트 순회 구조라서만
가능한 표현이었고, 지금은 "조회표에 KAKAO뿐인데 구글로 가입한 사용자가 탈퇴를 요청한다"가 되어
**진짜 일어날 수 있는 일**을 재현합니다. `supports` 스텁 8개가 전부 사라졌습니다.

### 확인한 것

- **`gradlew build --rerun-tasks` 통과.** 포트 계약이 바뀌므로 미구현이 있었다면 컴파일에서
  걸렸을 것입니다
- **`LayerDependencyTest` 10개 전부 통과.** 조립 클래스를 실수로 애플리케이션에 뒀다면 06번 규칙이
  잡았을 것입니다
- **스프링의 비-String 키 Map 주입을 따로 확인했습니다.** 스프링은 `Map<String, T>`만 "모든 빈을
  이름으로 모아 주는" 특례로 처리하고, 키가 `OAuthProvider`면 일반 타입 해석으로 이 `@Bean`을
  찾습니다. **이 가정이 깨지면 기동에서야 드러나는데** 컨텍스트 기동 테스트는 MySQL·Redis가
  필요한 `integrationTest`에 있습니다. 그래서 `OAuthClientConfigTest`에 스프링 코어만 쓰는
  `AnnotationConfigApplicationContext` 테스트를 하나 넣어 메웠습니다
- **탐색이 사라졌습니다.** `supports(`와 `List<OAuthClientPort>`는 이제 조립 지점의 파라미터와
  이력을 적은 Javadoc에만 남습니다
- **실제 부트 컨텍스트 기동은 이번에도 확인하지 못했습니다** — **남은 확인**입니다(04·05번과 동일)

## 5. 재발 방지

**자동 규칙을 두지 않았습니다.** "같은 다섯 줄이 두 곳에 있다"를 ArchUnit으로 표현할 수 없고,
표현할 수 있더라도 그 규칙은 소음이 됩니다.

**대신 구조가 막습니다.** 주입 타입이 `Map`이면 순회할 것이 없고, 따라서 순회 코드를 다시 쓸
자리도 없습니다. 새 소셜 제공자를 붙이는 사람은 어댑터에 `provider()` 하나를 선언할 뿐이고,
선택 코드는 쓸 일도 볼 일도 없습니다. **규칙보다 구조가 막는 편이 낫습니다.**

## 6. 하지 않기로 한 것

- **`OAuthClientPort`를 재설계하지 않았습니다.** `getUserInfo`·`unlink`·`getLoginUrl` 시그니처는
  그대로입니다. 다만 관찰 하나를 남깁니다 — **`unlink(provider, oauthId, ...)`가 provider를
  파라미터로 받습니다.** 어댑터가 이제 `provider()`로 자기 제공자를 선언하므로 그 인자는 중복이고,
  `getUserInfo(code, provider)`도 같습니다. 시그니처 변경은 이 항목의 범위 밖이라 새 항목 감입니다.
- **`KakaoOAuthAdapter.getUserInfo`의 provider 가드를 남겼습니다.** Map 디스패치 후에는 정상
  경로로 발화할 수 없습니다(키가 곧 provider이므로). **"발화할 수 없는 가드"가 하나 생긴 셈**이라
  적어 둡니다 — 어댑터를 직접 부르는 경우에 대한 방어이고 지금 이 가드를 덮는 테스트는 없어,
  없앨지는 위 시그니처 항목과 함께 판단하는 편이 낫습니다.
- **`AuthErrorCode.UNSUPPORTED_SOCIAL_LOGIN`을 없애지 않았습니다.**
- **01번보다 먼저 하지 않았습니다.** 먼저 했다면 곧 지울 파일까지 함께 고쳤을 것입니다.

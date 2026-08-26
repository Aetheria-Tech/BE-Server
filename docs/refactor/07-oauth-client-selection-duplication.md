# 07. OAuth 클라이언트 선택이 두 벌 중복

> 상태 · 대기
> 성격 · 중복 | 난이도 · 낮음 | 선행 항목 · [01번](01-dead-code.md) — **완료됨.** 세 벌 중 하나가 삭제로 사라져 **남은 것은 두 벌**입니다

## 1. 무엇이 문제인가

`OAuthProvider`로 알맞은 `OAuthClientPort` 구현체를 고르는 메서드가 **두 서비스에 그대로 두 번**
있습니다.

```java
private OAuthClientPort getClient(OAuthProvider provider) {
    return oAuthClients.stream()
            .filter(client -> client.supports(provider))
            .findFirst()
            .orElseThrow(() -> new AuthException(AuthErrorCode.UNSUPPORTED_SOCIAL_LOGIN, "지원하지 않는 소셜 로그인입니다."));
}
```

- [`LoginService`](../../src/main/java/com/serverbe/application/service/LoginService.java)
- [`WithdrawService`](../../src/main/java/com/serverbe/application/service/WithdrawService.java)

원래 세 벌이었습니다. 세 번째는 `SocialTokenService`에 있었고
**[01번](01-dead-code.md)에서 파일째 삭제되면서 함께 사라졌습니다.**

## 2. 근거

```bash
grep -rn "private OAuthClientPort getClient" src/main/java --include=*.java
```

두 서비스 모두 `private final List<OAuthClientPort> oAuthClients;`를 주입받고, 쓸 때마다
**리스트를 순회해 매번 다시 고릅니다.**

## 3. 왜 고쳐야 하는가

중복 자체는 크지 않습니다. 다섯 줄짜리이고, 01번을 닫은 지금은 두 곳뿐입니다.
**두 곳의 중복만으로 리팩터링을 정당화하기는 약합니다.**

진짜 이유는 다른 데 있습니다. 지금 구조는 **매 호출마다 "이 provider를 누가 지원하는지"를 다시
묻습니다.** 그 답은 애플리케이션 기동 시점에 이미 정해져 있고 절대 바뀌지 않습니다. 즉 런타임에
반복할 이유가 없는 탐색이고, 그 탐색을 위해 `OAuthClientPort`가 `supports(provider)`라는
**자기 소개 메서드**를 계약에 달고 있습니다.

포트 인터페이스에 `supports()`가 있다는 것은 **"나를 고르는 방법은 호출자가 알아서 하라"** 는
뜻입니다. 어댑터가 자기를 어떻게 선택할지까지 계약에 적을 필요는 없습니다.

## 4. 어떻게 — 두 갈래

### 갈래 A. 공통 헬퍼로 뽑는다

`OAuthClientSelector` 같은 컴포넌트를 만들어 두 서비스가 주입받습니다.

- 장점 — 변경이 작고, 지금 구조를 그대로 둡니다
- 단점 — **탐색은 그대로 남습니다.** `supports()`도 남습니다. 중복만 없앨 뿐 원인은 그대로입니다

### 갈래 B. 선택 자체를 없앤다 (권장)

`List<OAuthClientPort>` 대신 **`Map<OAuthProvider, OAuthClientPort>`** 를 주입받습니다. 스프링이
빈을 모아 줄 때 키를 만들어 주지는 않으므로, 설정 클래스에서 한 번 조립합니다.

```java
@Bean
Map<OAuthProvider, OAuthClientPort> oAuthClientsByProvider(List<OAuthClientPort> clients) { ... }
```

- 서비스 쪽은 `clients.get(provider)`로 끝나고, `orElseThrow`는 `null` 검사 한 줄이 됩니다
- **탐색이 기동 시점으로 옮겨 갑니다.** 지원하지 않는 provider는 런타임이 아니라 조립 시점에
  드러날 수 있습니다
- 조립 과정에서 **같은 provider를 두 어댑터가 지원하는 상황**(지금은 조용히 첫 번째가 이깁니다)을
  기동 실패로 만들 수 있습니다

**권장은 B입니다.** 다만 `supports()`를 포트에서 제거할지는 함께 판단합니다 — Map 조립에 여전히
그 정보가 필요하므로, 없애려면 어댑터마다 자기 `OAuthProvider`를 노출하는 다른 방법
(`provider()` 같은 단일 값 반환)이 필요합니다. **`supports(provider)`(질의)와 `provider()`(선언)의
차이가 이 판단의 핵심입니다** — 후자여야 Map의 키를 만들 수 있습니다.

## 5. 재발 방지

자동 규칙을 두지 않습니다. "같은 다섯 줄이 두 곳에 있다"를 ArchUnit으로 표현할 수 없고, 표현할 수
있더라도 그 규칙은 소음이 됩니다.

대신 갈래 B를 택하면 **구조적으로 재발하지 않습니다.** 주입 타입이 `Map`이면 순회할 것이 없고,
따라서 순회 코드를 다시 쓸 자리도 없습니다. **규칙보다 구조가 막는 편이 낫습니다.**

## 6. 하지 않기로 한 것

- **`OAuthClientPort` 자체를 재설계하지 않습니다.** `getUserInfo`·`unlink`·`getLoginUrl`의
  시그니처는 이 항목의 범위 밖입니다. (`refreshSocialToken`은 [01번](01-dead-code.md)에서
  죽은 메서드로 삭제되었습니다.)
- **`AuthErrorCode.UNSUPPORTED_SOCIAL_LOGIN`을 없애지 않습니다.** Map으로 바꿔도 `null`이 나오는
  경우(요청에 실린 provider 문자열이 변환은 됐는데 어댑터가 없는 경우)는 남고, 그때 던질 것이
  필요합니다.
- **01번보다 먼저 하지 않습니다.** 먼저 했다면 곧 지울 파일까지 함께 고쳤을 것입니다. 01번이
  완료되어 이 조건은 해소되었습니다.

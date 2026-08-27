# 01. 죽은 코드 — 세 건으로 시작해 아홉 곳

> 상태 · **완료** (커밋 전, 워킹 트리)
> 성격 · 정리 | 난이도 · 낮음 | 선행 항목 · 없음
> 이 항목을 닫으면서 [02번](02-transactional-on-mono.md)의 대상이 둘에서 하나로, [07번](07-oauth-client-selection-duplication.md)의 중복이 셋에서 둘로 줄었습니다.
> **착수해 보니 연쇄가 있었습니다** — 5절을 보세요. 문서에 적힌 3건이 아니라 9곳을 지웠습니다.

## 1. 무엇이 문제인가

빌드에 포함되고, 스프링 컨텍스트에 빈으로 올라가고, 다음 사람이 읽게 되지만 **아무도 부르지 않는
코드**가 세 건 있습니다.

| 대상 | 종류 |
| --- | --- |
| `application/service/SocialTokenService.java` | `@Service` 빈. 호출처 0건 |
| `application/port/out/dto/ai/RunningArtAiResponse.java` | 포트 DTO. 참조 0건 |
| `domain/service/` | `package-info.java` 하나만 남은 빈 패키지 |

> 위 셋은 지금 저장소에 없습니다. 그래서 파일 링크를 걸지 않았습니다 — **삭제된 파일은 링크할 수
> 없고**, 링크를 남겨 두면 문서 링크 검사가 깨집니다. 내용이 필요하면 git 이력에 있습니다.

## 2. 근거

아래는 **착수 시점의 조사**입니다. 지금은 대상이 사라져 아무것도 출력하지 않습니다 — 삭제 후의
확인은 5-3절에 따로 있습니다.

```bash
# 세 대상 모두 자기 자신 외의 참조가 없다
grep -rn "SocialTokenService\|getFreshAccessToken" src/main src/test --include=*.java
grep -rn "RunningArtAiResponse" src/ --include=*.java
ls src/main/java/com/serverbe/domain/service/
```

첫 두 명령은 **정의부만** 출력합니다. 세 번째는 `package-info.java` 하나만 나옵니다.

`SocialTokenService`는 인바운드 포트를 구현하지 않으므로(`implements` 절이 없습니다) 유스케이스로
호출될 경로 자체가 없고, 단위 테스트도 없습니다. 즉 **어느 방향에서도 진입점이 없습니다.**

## 3. 왜 이렇게 됐나

`SocialTokenService.getFreshAccessToken`은 "저장된 소셜 리프레시 토큰으로 소셜 액세스 토큰을 새로
받아 오고, 갱신된 리프레시 토큰이 함께 오면 DB에 다시 저장한다"는 일을 합니다.

**처음에 이 문서는 "갱신 책임이 어댑터로 흡수되면서 설 자리가 없어졌다"고 적었는데, 착수 조사에서
그게 아니라는 것이 드러났습니다.** 흡수된 것이 아니라 **처음부터 소비자가 없었습니다.**

소셜 액세스 토큰이 필요할 법한 곳은 회원 탈퇴 시 연동 해제 하나뿐인데, 두 어댑터의 `unlink`는
**어느 쪽도 소셜 액세스 토큰을 쓰지 않습니다.**

- **카카오** — `POST /v1/user/unlink`를 **admin key + `target_id`(=`oauthId`)** 로 호출합니다.
  사용자 토큰이 아예 관여하지 않습니다
- **구글** — `POST /revoke`에 **리프레시 토큰을 그대로 실어 폐기**합니다. 갱신할 이유가 없습니다

즉 `getFreshAccessToken`은 쓰일 자리를 잃은 것이 아니라 **한 번도 가진 적이 없습니다.** 포트에 미리
뚫어 둔 능력이 끝내 쓰이지 않은 경우이고,
[12번 문서](../troubleshooting/12-why-not-kafka.md)가 "가짜 이식성"이라 부른 것과 같은 모양입니다 —
**쓸 곳이 정해지기 전에 만든 추상화는 쓰이지 않습니다.**

`RunningArtAiResponse`도 같은 종류입니다. AI 응답 역직렬화는 지금
[`AiGenerationResultDto`](../../src/main/java/com/serverbe/application/port/in/dto/art/AiGenerationResultDto.java)가
맡고 있고, 이쪽은 그 전 세대의 형태입니다.

`domain/service/`는 도메인 서비스(엔티티 하나에 담기지 않는 도메인 규칙)를 두려고 만든 자리인데,
결과적으로 그런 규칙이 나오지 않았습니다. `PolylineUtils`가 `domain/util/`에 있고 값 객체들이 자기
규칙을 스스로 들고 있어서, 지금까지 빈 채입니다.

## 4. 왜 고쳐야 하는가

죽은 코드가 위험한 이유는 그것이 **읽히기 때문**입니다.

- `SocialTokenService`는 [02번 문서](02-transactional-on-mono.md)가 다루는 **잘못된 트랜잭션 패턴을
  그대로 시연**합니다. 다음 사람이 "이 저장소는 리액티브 메서드에 `@Transactional`을 붙이는구나"라고
  읽을 근거가 되고, 그 오해가 실제로 도는 코드로 복사됩니다.
- 세 곳에 중복된 `getClient` 중 하나가 여기 있습니다([07번](07-oauth-client-selection-duplication.md)).
  중복을 세는 눈이 흐려집니다.
- 빈 패키지는 `package-info.java`의 설명만 남아 **"여기에 도메인 서비스가 있다"고 잘못 안내**합니다.

## 5. 어떻게 — 그리고 실제로 무엇을 지웠나

문서에 적힌 3건으로 시작했지만, **`SocialTokenService`를 지우는 순간 연쇄가 드러났습니다.**

### 5-1. 연쇄 — 포트 메서드 하나가 함께 죽는다

`SocialTokenService`는 `OAuthClientPort.refreshSocialToken`의 **유일한 호출자**였습니다. 서비스가
사라지면 그 포트 메서드는 호출자가 0이 되고, 그러면 그것을 구현하는 어댑터·fallback·DTO가 줄줄이
따라옵니다. `User.renewOauthRefreshToken`도 마찬가지였습니다.

**여기서 멈출 수도 있었습니다.** 포트 메서드는 "능력"이니 남겨 둬도 컴파일은 됩니다. 그러나 그러면
**이 문서가 6절에서 정한 판별 기준에 그대로 걸리는 새 죽은 코드를 만들어 내는 셈**입니다. 게다가
불필요한 계약이 하나 남아, 앞으로 추가되는 모든 `OAuthClientPort` 구현체가 아무도 부르지 않을
메서드를 구현해야 합니다. **그래서 끝까지 지웠습니다.**

### 5-2. 최종 삭제 목록 — 9곳

| 층 | 대상 |
| --- | --- |
| 문서에 적힌 3건 | `SocialTokenService.java` · `RunningArtAiResponse.java` · `domain/service/`(패키지째) |
| 포트 | `OAuthClientPort#refreshSocialToken` |
| 어댑터 | `GoogleOAuthAdapter#refreshSocialToken` · `KakaoOAuthAdapter#refreshSocialToken` |
| fallback | `GoogleOAuthFallbackHandler#fallbackRefreshSocialToken` · `KakaoOAuthFallbackHandler#fallbackRefreshSocialToken` |
| 포트 DTO | `SocialTokenRefreshResult.java` |
| 도메인 | `User#renewOauthRefreshToken` |

### 5-3. 검증

- `./gradlew compileJava compileTestJava` 통과 — **삭제만으로 컴파일이 깨지지 않는 것이 "호출처가
  없었다"는 사실의 재확인**입니다
- `./gradlew test` 그대로 그린. `refreshSocialToken`·`SocialTokenRefreshResult`를 다루는 테스트는
  **애초에 하나도 없었습니다** — 죽은 코드였다는 또 하나의 증거입니다
- 삭제된 이름 다섯 개로 `src/` 전체를 다시 훑어 0건 확인

```bash
grep -rn "SocialTokenService\|RunningArtAiResponse\|refreshSocialToken\|SocialTokenRefreshResult\|renewOauthRefreshToken" src/
```

## 6. 재발 방지

죽은 코드를 자동으로 막는 규칙은 두지 않습니다. 스프링 빈은 DI로 연결되므로 "참조 0건"이라는
정적 신호가 곧 죽음을 뜻하지 않습니다 — 실제로 이 저장소의 아웃바운드 어댑터 대부분이 이름으로는
참조되지 않습니다. **오탐이 확실한 규칙은 규칙이 아니라 소음입니다.**

대신 **판별 기준**을 남깁니다. 다음 셋을 모두 만족하면 죽은 코드로 봅니다.

- 인터페이스(포트)를 구현하지 않는다 — 구현하면 DI로 주입될 자리가 있다는 뜻입니다
- `@Configuration`·`@RestControllerAdvice` 같은 프레임워크 훅이 아니다
- 이름으로 참조하는 곳이 자기 자신뿐이다

## 7. 하지 않기로 한 것

- **`SocialTokenService`의 로직을 어딘가로 옮기지 않았습니다.** 옮길 곳이 있었다면 이미 옮겨졌을
  것입니다. 소셜 액세스 토큰이 다시 필요해지면 그때 필요한 모양으로 새로 씁니다. git 이력에 남아
  있으므로 잃어버리는 것은 없습니다.
- **빈 패키지를 `package-info.java`만 남겨 두지 않았습니다.** "나중에 쓸 자리"라는 표시는 실제로
  쓰이지 않으면 잘못된 안내가 됩니다.
- **서킷브레이커 `googleTokenApi`·`kakaoTokenApi`는 남겼습니다.** `refreshSocialToken`만 쓰던 것이
  아니라 `getAccessToken`과 `unlink`도 같은 인스턴스를 씁니다. 죽은 메서드가 쓰던 것이라고 해서
  자동으로 죽은 것은 아닙니다 — **공유 여부를 확인하고 지워야 합니다.**
- **`User.oauthRefreshToken` 필드는 남겼습니다.** 구글 `unlink`가 이 값을 직접 폐기하고,
  `createNew`·`updateFromOAuth`도 씁니다. 지운 것은 **`renewOauthRefreshToken` 메서드 하나뿐**입니다.
- **`supports()`와 남은 `getClient` 중복 두 벌은 건드리지 않았습니다.**
  [07번](07-oauth-client-selection-duplication.md) 몫이었고, **거기서 닫혔습니다** — `supports()`는
  `provider()` 선언으로 바뀌었고 선택 코드 자체가 사라졌습니다.
- **`WithdrawService`의 `@Transactional`은 그대로입니다.**
  [02번](02-transactional-on-mono.md) 몫이고, 이 작업으로 그 문서의 대상이 하나 줄었을 뿐입니다.

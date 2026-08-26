# 01. 죽은 코드 세 건

> 상태 · 대기
> 성격 · 정리 | 난이도 · 낮음 | 선행 항목 · 없음
> 이 항목을 닫으면 [02번](02-transactional-on-mono.md)의 대상이 둘에서 하나로, [07번](07-oauth-client-selection-duplication.md)의 중복이 셋에서 둘로 줄어듭니다.

## 1. 무엇이 문제인가

빌드에 포함되고, 스프링 컨텍스트에 빈으로 올라가고, 다음 사람이 읽게 되지만 **아무도 부르지 않는
코드**가 세 건 있습니다.

| 대상 | 종류 |
| --- | --- |
| [`application/service/SocialTokenService.java`](../../src/main/java/com/serverbe/application/service/SocialTokenService.java) | `@Service` 빈. 호출처 0건 |
| [`application/port/out/dto/ai/RunningArtAiResponse.java`](../../src/main/java/com/serverbe/application/port/out/dto/ai/RunningArtAiResponse.java) | 포트 DTO. 참조 0건 |
| `domain/service/` | `package-info.java` 하나만 남은 빈 패키지 |

## 2. 근거

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

그런데 이 서버가 **소셜 액세스 토큰을 실제로 필요로 하는 곳은 회원 탈퇴 시 연동 해제 한 곳뿐**이고,
그 경로는 [`WithdrawService`](../../src/main/java/com/serverbe/application/service/WithdrawService.java)가
`OAuthClientPort.unlink`에 리프레시 토큰을 그대로 넘겨 어댑터 안에서 처리합니다. 즉 갱신 책임이
어댑터로 흡수되면서 이 서비스가 설 자리가 없어졌고, **삭제되지 않은 채 남았습니다.**

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

## 5. 어떻게

세 건 모두 단순 삭제입니다.

1. `SocialTokenService.java` 삭제. 컴파일이 깨지지 않는 것으로 호출처 없음이 다시 확인됩니다.
2. `RunningArtAiResponse.java` 삭제.
3. `domain/service/` 디렉터리를 `package-info.java`째 삭제. 도메인 서비스가 실제로 필요해지는 날
   그때 만드는 편이, 빈 자리를 남겨 두는 것보다 낫습니다.

`./gradlew test`가 그대로 통과해야 합니다. 삭제만으로 무언가 깨진다면 그것은 **이 조사가 놓친
참조**이므로, 깨진 지점을 먼저 문서에 반영하고 판단을 다시 합니다.

## 6. 재발 방지

죽은 코드를 자동으로 막는 규칙은 두지 않습니다. 스프링 빈은 DI로 연결되므로 "참조 0건"이라는
정적 신호가 곧 죽음을 뜻하지 않습니다 — 실제로 이 저장소의 아웃바운드 어댑터 대부분이 이름으로는
참조되지 않습니다. **오탐이 확실한 규칙은 규칙이 아니라 소음입니다.**

대신 **판별 기준**을 남깁니다. 다음 셋을 모두 만족하면 죽은 코드로 봅니다.

- 인터페이스(포트)를 구현하지 않는다 — 구현하면 DI로 주입될 자리가 있다는 뜻입니다
- `@Configuration`·`@RestControllerAdvice` 같은 프레임워크 훅이 아니다
- 이름으로 참조하는 곳이 자기 자신뿐이다

## 7. 하지 않기로 한 것

- **`SocialTokenService`의 로직을 어딘가로 옮기지 않습니다.** 옮길 곳이 있었다면 이미 옮겨졌을
  것입니다. 소셜 액세스 토큰이 다시 필요해지면 그때 필요한 모양으로 새로 씁니다. git 이력에 남아
  있으므로 잃어버리는 것은 없습니다.
- **빈 패키지를 `package-info.java`만 남겨 두지 않습니다.** "나중에 쓸 자리"라는 표시는 실제로
  쓰이지 않으면 잘못된 안내가 됩니다.

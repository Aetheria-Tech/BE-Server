# 09. `TokenPersistencePort`가 두 책임을 쥔다

> 상태 · **완료 · 커밋 `262dbe5`**
> 성격 · 응집도 | 난이도 · 중간 | **선행 항목 · [11번 — 테스트 공백](11-test-gaps-persistence-adapters.md)** (그물 부분 완료)
> 테스트 없이 나누면 나눈 것이 맞는지 알 방법이 없습니다. **그물을 먼저 쳤습니다.**
> **문서의 예측이 한 군데 틀렸습니다** — 두 갈래는 키 하나를 공유하고 있었습니다. 3절을 보세요.

## 1. 무엇이 문제였는가

`TokenPersistencePort`에 메서드가 14개 있었고, **두 갈래로 정확히 갈렸습니다.**

| 갈래 | 메서드 |
| --- | --- |
| **리프레시 토큰 세션 관리** (10개) | `saveRefreshToken` · `getRefreshToken` · `deleteRefreshToken` · `deleteAllRefreshTokens` · `getAllDeviceIds` · `removeOldestSession` · `getSessionCount` · `rotateRefreshToken` · `existsRefreshToken` · `getSessionTtl` |
| **블랙리스트** (4개) | `blacklistAccessToken` · `blacklistRefreshToken` · `isAccessTokenBlacklisted` · `isRefreshTokenBlacklisted` |

구현체 `TokenPersistenceAdapter`는 **365줄로 이 저장소에서 가장 큰 클래스**였고, **단위 테스트가
없었습니다.**

## 2. 근거

착수 전에 확인한 명령입니다. 지금은 두 파일이 답하므로 결과가 달라집니다.

```bash
# 착수 전: 한 포트에 14개
grep -c "^    [a-zA-Z].*(" src/main/java/com/serverbe/application/port/out/token/*.java
# 지금: RefreshTokenSessionPort 10 · TokenBlacklistPort 4

wc -l src/main/java/com/serverbe/adapter/out/persistence/token/*.java
find src/test -path "*persistence/token/*Test.java"
```

## 3. 왜 갈렸는가 — 두 갈래는 다른 것을 저장한다

**세션 쪽**은 `userId + deviceId`로 키를 잡고, 사용자당 세션 수를 세고, 가장 오래된 것을 밀어내고,
회전 시 옛것과 새것을 원자적으로 바꿉니다. **키의 주인이 사용자**이고, "1인 N기기" 정책이 여기
살아 있습니다.

**블랙리스트 쪽**은 토큰 문자열 자체를 키로 잡고 TTL이 끝날 때까지 존재 여부만 봅니다.
**키의 주인이 토큰**이고, 사용자를 모릅니다.

둘은 같은 Redis를 쓸 뿐 **데이터 모델도, 수명 정책도, 질문하는 주체도 다릅니다.** 세션은
"이 기기가 아직 유효한가"를 묻고, 블랙리스트는 "이 토큰이 죽었는가"를 묻습니다.

Lua 스크립트 배치가 그 경계를 이미 드러냈습니다 — 스크립트 4개
(`saveToken` · `rotateToken` · `globalLogout` · `deleteToken`)는 **전부 세션 쪽**입니다. 원자성이
필요한 복합 연산이 한쪽에만 몰려 있다는 뜻이고, 그건 두 갈래의 복잡도가 다르다는 신호였습니다.

### 예측이 틀린 지점 — 두 갈래는 키 하나를 공유한다

이 문서는 착수 전에 **"프리픽스 3개+스크립트 4개와 프리픽스 2개로 갈리므로 겹치는 것이 거의
없다"**고 적었습니다. **틀렸습니다.**

`rotateRefreshToken`이 **구 토큰의 RT 블랙리스트 키**(`BL:RT:{sha256}`)를 만들어
`rotate_token.lua`의 `KEYS[3]`으로 넘깁니다. 회전은 구 토큰 무효화와 신 토큰 발급을 한 스크립트로
묶어야 하고([6번 트러블슈팅](../troubleshooting/06-refresh-token-rotation.md)), 그러려면 세션
어댑터가 블랙리스트 키 조립 규칙을 알아야 합니다.

**갈라 보기 전에는 보이지 않던 사실입니다.** 한 클래스 안에서는 `createRefreshTokenBlacklistKey`를
누가 부르든 그냥 private 메서드 호출이었고, 그것이 경계를 넘는 호출이라는 사실이 드러나지
않았습니다.

## 4. 왜 고쳤는가

포트가 넓으면 **테스트 대역(fake)을 만들 때마다 14개를 전부 구현해야 합니다.** 세션만 쓰는
테스트도 블랙리스트 메서드를 채워야 하고, 그 빈 구현이 "이 테스트가 무엇에 의존하지 않는지"를
가려 버립니다.

호출자 쪽에서 비용이 실제로 보였습니다. `JwtAuthenticationFilter`는 **블랙리스트 조회 하나만**
쓰면서 14개짜리 포트를 주입받고 있었고, `UserDataCleanupManager`는 **전역 로그아웃 하나만**
썼습니다.

## 5. 어떻게 했는가 — 순서가 중요했다

### 1단계 · 테스트를 먼저 씌웠다

분할 **전에** `TokenPersistenceAdapterTest`를 써서 17개 단언을 초록으로 만들었습니다. 본보기는
저장소 안에 이미 있었습니다 — `RateLimitPersistenceAdapterTest`(Lua 인자)와
`AiTaskRedisAdapterTest`(키·TTL). **새로 설계할 것이 없었습니다.**

고정한 것은 두 가지입니다.

| 대상 | 모양 |
| --- | --- |
| 토큰 키 | `user:100:rt:device-A` |
| 세션 인덱스 | `user:session:100` |
| 스크립트용 접두사 | `user:100:rt:` |
| AT 블랙리스트 | `BL:AT:{sha256(token)}` |
| RT 블랙리스트 | `BL:RT:{sha256(token)}` |

그리고 **어느 스크립트에 어떤 KEYS/ARGV가 넘어가는지**입니다. 스크립트를 `ArgumentCaptor`로 함께
잡아 동일성까지 확인합니다 — 어댑터가 스크립트를 넷 들고 있어서, 키와 인자가 맞아도 **다른
스크립트**에 넘어가면 전혀 다른 동작이 됩니다.

**어디까지 덮이는지는 [11번](11-test-gaps-persistence-adapters.md)에 적었습니다.** 목으로는
"스크립트에 이런 인자를 넘겼다"까지이고, 스크립트가 실제로 원자적으로 도는지는 여기서 알 수
없습니다.

### 2단계 · 포트를 나눴다

```
RefreshTokenSessionPort   ← 10개
TokenBlacklistPort        ←  4개
```

javadoc은 **옮기지 않고 다시 썼습니다.** 기존 포트 머리의 `@responsibility`는 두 책임을 접속사로
잇고 있어서, 나눈 뒤에는 각각 절반만 사실이 됩니다.

### 3단계 · 어댑터도 나눴다 — 키 조립기를 공유한다

3절에서 드러난 공유 때문에 **키 조립을 `TokenRedisKeys`로 뽑았습니다.** 프리픽스 5개와 키 조립
5개가 여기 한 곳에만 남고, SHA-256 해싱도 마찬가지입니다.

| 클래스 | 줄 수 | 하는 일 |
| --- | --- | --- |
| `RefreshTokenSessionAdapter` | 221 | 세션 10개 + 스크립트 4개 |
| `TokenBlacklistAdapter` | 93 | 블랙리스트 4개 |
| `TokenRedisKeys` | 79 | 키 조립 5개 |

**양쪽이 각자 키를 조립하게 두는 선택지도 있었지만 택하지 않았습니다.** 그러면 한쪽 모양이 바뀌는
날 다른 쪽이 조용히 어긋나고, 증상은 "회전한 구 토큰이 계속 유효하다"로 나타납니다. 컴파일도
테스트도 통과하면서 말입니다.

### 4단계 · 호출자를 정리했다

| 호출자 | 주입받는 것 |
| --- | --- |
| `AuthSessionManager` | **둘 다** |
| `UserDataCleanupManager` | `RefreshTokenSessionPort` |
| `JwtAuthenticationFilter` | `TokenBlacklistPort` |

`AuthSessionManager`가 둘을 받는 것은 괜찮습니다 — 세션 종료 시 액세스 토큰을 블랙리스트에 넣는
것은 실제로 두 갈래를 함께 쓰는 유스케이스입니다. **오히려 그 지점이 명시적으로 드러나는 것이 이
리팩터링의 이득입니다.**

### 5단계 · 테스트를 둘로 나눴다

`RefreshTokenSessionAdapterTest`(12개)와 `TokenBlacklistAdapterTest`(7개)로 옮기면서 **단언은 한
줄도 바꾸지 않았습니다.** 1단계의 초록이 5단계에서도 초록인 것이 "동작이 안 바뀌었다"의 근거입니다.

`TokenRedisKeys`는 두 테스트가 **실제 인스턴스로** 공유합니다. 목으로 두면 고정하려던 키 모양
자체가 사라집니다.

## 6. 쪼개니 드러난 것

### 죽은 catch 분기 하나

`isAccessTokenBlacklisted`가 **JPA의** `jakarta.persistence.QueryTimeoutException`을 잡고
있었습니다. Redis가 던지는 것은 **스프링 Data의** `org.springframework.dao.QueryTimeoutException`
입니다. 이름이 같고 패키지만 다릅니다.

그래서 의도했던 **보안 감사 로그**(어느 토큰이 검증을 우회했는지 SHA-256 해시로 남기는 분기)는
한 번도 실행되지 않았고, 타임아웃은 아래 `DataAccessException` 분기로 떨어져 평범한 에러 로그만
남겼습니다. **반환값이 양쪽 다 `false`여서 증상이 없었습니다** — [02번](02-transactional-on-mono.md)·
[03번](03-stale-javadoc-after-listener-split.md)이 말한 "동작이 틀리지 않은 결함이 더 오래 산다"의
또 다른 사례입니다.

import을 스프링 쪽으로 바꿔 분기를 되살렸고, `TokenBlacklistAdapterTest`에 타임아웃 케이스를
추가해 다시 죽지 못하게 했습니다. **인증 동작은 바뀌지 않았습니다** — fail-open은 그대로이고
바뀐 것은 로그 경로뿐입니다.

**06번과 같은 함정입니다.** [06번](06-framework-exception-leak.md)이 잡아낸 것도 "도메인에 이름이
같은 예외가 있어 import를 봐야만 구분되는" 상황이었고, 여기서는 IDE 자동 import가 JPA 쪽을 골라
간 것으로 보입니다.

### 두 조회 경로의 비대칭 — 판단 대기

`isAccessTokenBlacklisted`는 예외를 잡아 fail-open하지만 `isRefreshTokenBlacklisted`는 잡지
않습니다. 매 요청마다 불리는 경로와 재발급 경로의 차이라고 볼 수 있지만, **의도인지 누락인지 코드는
말해 주지 않습니다.** 고치는 것은 동작 변경이라 이번 범위에서 제외하고 사실만 남깁니다 —
어댑터 javadoc에도 적어 두었습니다.

## 7. 확인한 것

- **`gradlew test` 두 번 초록** — 분할 전 17개, 분할 후 19개(타임아웃 케이스 2개 추가).
  **같은 단언이 두 번 통과한 것이 이 항목의 근거입니다**
- **`gradlew build --rerun-tasks` 통과.** ArchUnit `아웃바운드_포트_구현체는_어댑터다`가 새 어댑터
  둘을 그대로 통과합니다. `TokenRedisKeys`는 포트를 구현하지 않으므로 이 규칙의 대상이 아닙니다
- **컨텍스트 기동을 실제로 확인했습니다** (`gradlew integrationTest --tests ServerBeApplicationTests`).
  **08번이 "남은 확인"으로 넘긴 것을 여기서는 해야 했습니다** — 빈이 하나에서 셋으로 늘고
  `@Qualifier` 스크립트 주입이 다른 클래스로 옮겨 가므로, 08번과 달리 배선이 실제로 바뀝니다
- 커밋 `262dbe5`. 머리말에 해시를 덧붙였습니다

## 8. 재발 방지

"포트 메서드는 N개 이하"라는 규칙은 두지 않았습니다. 옳은 숫자가 없고,
[08번](08-fat-service-runningart.md)과 같은 이유로 소음이 됩니다.

대신 판단 기준을 `application/port/package-info.java`에 적었습니다.

> **한 포트의 메서드들이 같은 키 모델을 공유하지 않으면 두 포트입니다.** 무엇으로 찾는가
> (`userId+deviceId`인가 토큰 문자열인가)가 갈리는 순간, 그 둘은 같은 저장소를 쓸 뿐 같은 개념이
> 아닙니다.

**여기에 한 문장을 덧붙였습니다** — 3절에서 배운 것입니다.

> **두 포트를 함께 주입받는 호출자가 생기는 것은 실패가 아닙니다.** 실제로 두 갈래를 함께 쓰는
> 유스케이스가 있고, 넓은 포트 하나는 그 지점을 드러내는 대신 감춥니다.

## 9. 하지 않기로 한 것

- **Lua 스크립트를 손대지 않았습니다.** 원자성 보장은 [5번 트러블슈팅](../troubleshooting/05-rate-limit-lua-token-bucket.md)과
  [6번](../troubleshooting/06-refresh-token-rotation.md)의 결론이고, 포트를 나누는 것과 무관합니다.
  네 파일 전부 무수정입니다.
- **블랙리스트를 Redis 밖으로 옮기지 않았습니다.** TTL 기반 만료가 Redis의 강점이고, 대안을 찾을
  이유가 없습니다.
- **`isRefreshTokenBlacklisted`에 try/catch를 붙이지 않았습니다.** 6절의 비대칭은 사실로만
  기록합니다.
- **11번의 나머지 세 어댑터는 다루지 않았습니다.** 09번의 조건은 `TokenPersistenceAdapter`까지였고,
  `RunningArtPersistenceAdapter`·`AiTaskPersistenceAdapter`·`RunningArtRedisAdapter`는 11번에
  남아 있습니다.

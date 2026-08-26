# 09. `TokenPersistencePort`가 두 책임을 쥔다

> 상태 · 대기
> 성격 · 응집도 | 난이도 · 중간 | **선행 항목 · [11번 — 테스트 공백](11-test-gaps-persistence-adapters.md)**
> 테스트 없이 나누면 나눈 것이 맞는지 알 방법이 없습니다. **그물을 먼저 칩니다.**

## 1. 무엇이 문제인가

[`TokenPersistencePort`](../../src/main/java/com/serverbe/application/port/out/token/TokenPersistencePort.java)에
메서드가 14개 있고, **두 갈래로 정확히 갈립니다.**

| 갈래 | 메서드 |
| --- | --- |
| **리프레시 토큰 세션 관리** (10개) | `saveRefreshToken` · `getRefreshToken` · `deleteRefreshToken` · `deleteAllRefreshTokens` · `getAllDeviceIds` · `removeOldestSession` · `getSessionCount` · `rotateRefreshToken` · `existsRefreshToken` · `getSessionTtl` |
| **블랙리스트** (4개) | `blacklistAccessToken` · `blacklistRefreshToken` · `isAccessTokenBlacklisted` · `isRefreshTokenBlacklisted` |

구현체 [`TokenPersistenceAdapter`](../../src/main/java/com/serverbe/adapter/out/persistence/token/TokenPersistenceAdapter.java)는
**365줄로 이 저장소에서 가장 큰 클래스**이고, **단위 테스트가 없습니다.**

## 2. 근거

```bash
grep -n "    [A-Za-z].*(" src/main/java/com/serverbe/application/port/out/token/TokenPersistencePort.java
wc -l src/main/java/com/serverbe/adapter/out/persistence/token/TokenPersistenceAdapter.java
find src/test -name "TokenPersistenceAdapterTest.java"   # 결과 없음
```

## 3. 왜 갈리는가 — 두 갈래는 다른 것을 저장합니다

**세션 쪽**은 `userId + deviceId`로 키를 잡고, 사용자당 세션 수를 세고, 가장 오래된 것을 밀어내고,
회전 시 옛것과 새것을 원자적으로 바꿉니다. **키의 주인이 사용자**이고, "1인 N기기" 정책이 여기
살아 있습니다.

**블랙리스트 쪽**은 토큰 문자열 자체를 키로 잡고 TTL이 끝날 때까지 존재 여부만 봅니다.
**키의 주인이 토큰**이고, 사용자를 모릅니다.

둘은 같은 Redis를 쓸 뿐 **데이터 모델도, 수명 정책도, 질문하는 주체도 다릅니다.** 세션은
"이 기기가 아직 유효한가"를 묻고, 블랙리스트는 "이 토큰이 죽었는가"를 묻습니다.

Lua 스크립트 배치가 그 경계를 이미 드러냅니다 — 어댑터가 들고 있는 스크립트 4개
(`saveToken` · `rotateToken` · `globalLogout` · `deleteToken`)는 **전부 세션 쪽**입니다. 원자성이
필요한 복합 연산이 한쪽에만 몰려 있다는 뜻이고, 그건 두 갈래의 복잡도가 다르다는 신호입니다.

## 4. 왜 고쳐야 하는가

포트가 넓으면 **테스트 대역(fake)을 만들 때마다 14개를 전부 구현해야 합니다.** 세션만 쓰는
테스트도 블랙리스트 메서드를 채워야 하고, 그 빈 구현이 "이 테스트가 무엇에 의존하지 않는지"를
가려 버립니다.

읽는 쪽도 마찬가지입니다. `AuthSessionManager`는 이 포트 하나로 세션과 블랙리스트를 모두 다루는데,
포트를 보는 것만으로는 **어느 메서드가 어느 정책에 속하는지** 알 수 없습니다.

## 5. 어떻게 — 순서가 중요합니다

**1단계 · 테스트를 먼저 씌웁니다** ([11번](11-test-gaps-persistence-adapters.md))

Lua 스크립트 4개의 원자성이 이 어댑터의 핵심이므로, 목(mock) 기반 단위 테스트만으로는 부족합니다.
**어느 수준까지 검증할지를 11번에서 결정한 뒤** 이 항목으로 돌아옵니다.

**2단계 · 포트를 나눕니다**

```
RefreshTokenSessionPort   ← 10개
TokenBlacklistPort        ←  4개
```

**3단계 · 어댑터를 어떻게 할지 판단합니다**

두 갈래로 나눌 수도, 한 어댑터가 두 포트를 구현하게 둘 수도 있습니다. **판단 근거는 Redis 연결과
키 프리픽스 설정을 공유하느냐**입니다. 지금 어댑터는 생성자에서 프리픽스 5개와 스크립트 4개를
받는데, 나눠 보면 **프리픽스 3개+스크립트 4개**와 **프리픽스 2개**로 갈립니다. 겹치는 것이 거의
없으므로 어댑터도 나누는 쪽이 자연스러워 보이지만, **실제로 갈라 보고 판단합니다.**

**4단계 · 호출자를 정리합니다**

`AuthSessionManager`가 두 포트를 모두 주입받게 됩니다. 그 자체는 괜찮습니다 — 세션 종료 시
액세스 토큰을 블랙리스트에 넣는 것은 실제로 두 갈래를 함께 쓰는 유스케이스입니다. **오히려 그
지점이 명시적으로 드러나는 것이 이 리팩터링의 이득입니다.**

## 6. 재발 방지

"포트 메서드는 N개 이하"라는 규칙은 두지 않습니다. 옳은 숫자가 없고,
[08번](08-fat-service-runningart.md)과 같은 이유로 소음이 됩니다.

대신 판단 기준을 남깁니다.

> **한 포트의 메서드들이 같은 키 모델을 공유하지 않으면 두 포트입니다.** 무엇으로 찾는가
> (`userId+deviceId`인가 토큰 문자열인가)가 갈리는 순간, 그 둘은 같은 저장소를 쓸 뿐 같은 개념이
> 아닙니다.

## 7. 하지 않기로 한 것

- **Lua 스크립트를 손대지 않습니다.** 원자성 보장은 [5번 트러블슈팅](../troubleshooting/05-rate-limit-lua-token-bucket.md)과
  [6번](../troubleshooting/06-refresh-token-rotation.md)의 결론이고, 포트를 나누는 것과 무관합니다.
- **블랙리스트를 Redis 밖으로 옮기지 않습니다.** TTL 기반 만료가 Redis의 강점이고, 대안을 찾을
  이유가 없습니다.
- **11번보다 먼저 하지 않습니다.** 이 문서의 선행 항목 표시는 권고가 아니라 **조건**입니다.

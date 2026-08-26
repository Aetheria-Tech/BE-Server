# 11. 영속성 어댑터 테스트 공백

> 상태 · 대기
> 성격 · 테스트 | 난이도 · 중간 | 선행 항목 · 없음
> **[09번](09-fat-port-token-persistence.md)의 조건입니다.** 테스트 없이 나누면 나눈 것이 맞는지 알 방법이 없습니다.

## 1. 무엇이 문제인가

영속성 어댑터 일곱 중 셋에 단위 테스트가 있고, 넷에는 없습니다. **없는 쪽이 큰 쪽입니다.**

| 어댑터 | 줄 수 | 테스트 |
| --- | --- | --- |
| `TokenPersistenceAdapter` | 365 | **없음** — 저장소 최대 클래스 |
| `RunningArtPersistenceAdapter` | 206 | **없음** |
| `AiTaskPersistenceAdapter` | 105 | **없음** |
| `RunningArtRedisAdapter` | 88 | **없음** |
| `UserPersistenceAdapter` | — | 있음 |
| `RateLimitPersistenceAdapter` | — | 있음 |
| `AiTaskRedisAdapter` | — | 있음 |

## 2. 근거

```bash
# main 클래스 중 대응하는 *Test 가 없는 것
for f in $(find src/main/java/com/serverbe/adapter/out/persistence -name "*Adapter.java"); do
  cls=$(basename "$f" .java)
  [ -z "$(find src/test -name "${cls}Test.java")" ] && echo "테스트 없음: $cls"
done
```

`TokenPersistenceAdapter`를 건드리는 테스트가 아예 없지는 않습니다 — `BlacklistPerformanceTest`가
블랙리스트 조회 성능을 잽니다. 하지만 그건 **성능 측정이지 동작 검증이 아니고**, 세션 관리 쪽
10개 메서드는 아무것도 덮지 않습니다.

## 3. 왜 고쳐야 하는가

**패턴이 이미 저장소 안에 있다는 것이 이 항목의 핵심입니다.** 새로 설계할 것이 없습니다.

- [`UserPersistenceAdapterTest`](../../src/test/java/com/serverbe/adapter/out/persistence/user/UserPersistenceAdapterTest.java) —
  JPA 리포지토리를 목으로 두고 **매퍼 왕복과 예외 번역**을 검증하는 모양
- [`AiTaskRedisAdapterTest`](../../src/test/java/com/serverbe/adapter/out/persistence/task/AiTaskRedisAdapterTest.java) —
  Redis 템플릿을 목으로 두고 **키 조립과 TTL**을 검증하는 모양
- [`RateLimitPersistenceAdapterTest`](../../src/test/java/com/serverbe/adapter/out/persistence/ratelimit/RateLimitPersistenceAdapterTest.java) —
  **Lua 스크립트 호출 인자**를 검증하는 모양. `TokenPersistenceAdapter`에 가장 가까운 본보기입니다

세 파일이 각각 다른 종류의 검증을 이미 보여 주고 있고, 빈 곳 셋은 그 셋의 조합입니다.

그리고 **[09번](09-fat-port-token-persistence.md)이 이 항목을 기다리고 있습니다.** 365줄짜리
어댑터를 둘로 가르는 작업에서 "동작이 안 바뀌었다"를 무엇이 말해 줄 것인가 — 지금은 답이
없습니다.

## 4. 어떻게

### `TokenPersistenceAdapter` — 가장 크고 가장 급합니다

두 갈래를 나눠서 접근합니다.

**키 조립과 단순 연산** — 목 기반 단위 테스트로 충분합니다. `createTokenKey`·
`createSessionIndexKey`·`createAccessTokenBlacklistKey`가 만드는 키 모양이 프리픽스 설정과 함께
고정되어야 합니다. **키 모양이 바뀌면 기존 세션이 전부 무효가 되므로**, 이건 JSON 계약
(`RestApiResponseJsonContractTest`)과 같은 성격의 고정입니다.

**Lua 스크립트 4개** — `saveToken`·`rotateToken`·`globalLogout`·`deleteToken`. 여기가 어려운
지점입니다. 목으로는 **"스크립트에 이런 인자를 넘겼다"까지만** 검증되고, 스크립트가 실제로 원자적으로
동작하는지는 알 수 없습니다. `RateLimitPersistenceAdapterTest`가 딱 그 선까지 가 있습니다.

**그 선을 넘을지 판단해야 합니다.** 넘으려면 임베디드 Redis나 Testcontainers가 필요하고, 지금
테스트 환경에는 없습니다. 판단 기준은 이렇습니다 — **[6번 트러블슈팅](../troubleshooting/06-refresh-token-rotation.md)이
기록한 회전 경합이 목 기반 테스트로 잡히는가?** 잡히지 않는다면 그 시나리오만은 실제 Redis가
필요하다는 뜻이고, 그때 도입 비용을 별도 항목으로 엽니다.

**이 판단을 먼저 내리고 09번으로 갑니다.** 어디까지 덮이는지 모르는 채로 포트를 나누면 안 됩니다.

### `RunningArtPersistenceAdapter`

Querydsl 동적 쿼리와 페이징이 들어 있어 목 기반으로는 얕게밖에 못 덮습니다. 다만
`RunningArtPageJsonContractTest`와 `PageQueryMapperTest`가 페이징 계약의 일부를 이미 잡고 있으므로,
**그 둘이 덮지 못하는 것**부터 채웁니다 — 소유자 필터가 실제로 쿼리에 들어가는지, `findAllByIdIn`이
빈 리스트를 받았을 때 어떻게 되는지.

### `AiTaskPersistenceAdapter`

가장 작고 가장 값이 큽니다. `save`가 스프링의 `DataIntegrityViolationException`을
`AiException(DUPLICATE_AI_REQUEST)`으로 번역하는 분기가 **동시 요청 차단의 마지막 방어선**인데
지금 아무것도 검증하지 않습니다. [06번 문서](06-framework-exception-leak.md)가 이 번역 패턴을
`UserPersistenceAdapter`에도 적용하자고 하므로, **여기 테스트가 그 본보기가 됩니다.**

## 5. 재발 방지

"모든 클래스에 테스트가 있어야 한다"는 규칙은 두지 않습니다. DTO와 매퍼까지 걸려 소음이 됩니다.

대신 좁은 규칙 하나를 검토합니다.

```
아웃바운드_포트_구현체는_테스트를_가진다
  adapter.out.. 에서 application.port.out.. 을 구현하는 클래스는
  대응하는 *Test 가 존재해야 한다
```

**단, 이 항목을 닫은 뒤에 켭니다.** 지금 켜면 즉시 빨간불이고, 빨간 채로 두는 규칙은
아무도 믿지 않게 됩니다. 규칙은 초록일 때 켜야 의미가 있습니다 — 기존 `LayerDependencyTest`가
"현재 지켜지고 있는 규칙을 고정한다"는 방식으로 도입된 것과 같습니다(커밋 `60943b5`).

## 6. 하지 않기로 한 것

- **커버리지 수치를 목표로 삼지 않습니다.** 덮어야 할 것은 줄이 아니라 **분기**이고, 특히
  "실패했을 때 무엇이 일어나는가"입니다.
- **통합 테스트 인프라를 이 항목에서 도입하지 않습니다.** 필요하다는 판단이 서면 별도 항목으로
  엽니다. 이 항목은 **목으로 덮을 수 있는 것을 덮고, 덮을 수 없는 것이 무엇인지 명확히 하는
  데까지**입니다.
- **컨트롤러와 리졸버의 테스트 공백은 다루지 않습니다.** `RunningArtControllerTest`가 있고 나머지
  컨트롤러에는 없지만, 컨트롤러는 대부분 위임 한 줄이라 우선순위가 낮습니다.

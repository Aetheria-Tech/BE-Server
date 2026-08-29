# 11. 영속성 어댑터 테스트 공백

> 상태 · **완료** (커밋 전, 워킹 트리)
> 성격 · 테스트 | 난이도 · 중간 | 선행 항목 · 없음
> **[09번](09-fat-port-token-persistence.md)의 조건이었습니다.** 테스트 없이 나누면 나눈 것이 맞는지 알 방법이 없습니다.
> **문서가 센 범위가 틀렸습니다** — 테스트 없는 아웃바운드 구현체는 셋이 아니라 열이었고, 나머지는 [12번](12-test-gaps-outbound-adapters.md)으로 열었습니다.

## 1. 무엇이 문제인가

영속성 어댑터 일곱 중 셋에 단위 테스트가 있고, 넷에는 없었습니다. **없는 쪽이 큰 쪽이었습니다.**

| 어댑터 | 줄 수 | 테스트 |
| --- | --- | --- |
| ~~`TokenPersistenceAdapter`~~ → `RefreshTokenSessionAdapter` · `TokenBlacklistAdapter` | 221 · 93 | ✔ **생김** (12개 · 7개) — 09번에서 |
| `RunningArtPersistenceAdapter` | 206 | ✔ **생김** (11개) |
| `AiTaskPersistenceAdapter` | 105 | ✔ **생김** (10개) |
| `RunningArtRedisAdapter` | 88 | ✔ **생김** (6개) |
| `UserPersistenceAdapter` | — | 있음 |
| `RateLimitPersistenceAdapter` | — | 있음 |
| `AiTaskRedisAdapter` | — | 있음 |

토큰 어댑터가 365줄 한 덩어리에서 둘로 갈린 것은 09번의 결과입니다. **그물을 먼저 치고 잘랐고,
같은 단언이 자르기 전과 후에 모두 통과했습니다.**

### 그런데 이 표가 세는 범위가 틀렸습니다

이 문서는 **"영속성 어댑터"만** 셌지만, 5절에서 켜자고 한 규칙은 `adapter.out..` **전체**를 덮습니다.
실제로 세어 보니 테스트가 없는 아웃바운드 포트 구현체는 셋이 아니라 **열**이었습니다.

| 이 항목이 덮은 것 | 남은 것 → [12번](12-test-gaps-outbound-adapters.md) | 규칙에서 제외 |
| --- | --- | --- |
| 위 표의 셋 | `JwtTokenResolver` 226 · `S3AiOutputAdapter` 151 · `JwtTokenProvider` 137 · `SageMakerAsyncAdapter` 72 | `@Profile` 페이크 셋 |

**`JwtTokenResolver`(226줄)가 이제 저장소에서 가장 큰 무테스트 클래스입니다.** 놓친 이유가
분명합니다 — [04번](04-outbound-adapter-location.md)이 그 둘을 `infrastructure`에서
`adapter.out.security`로 옮겼는데, **이 문서는 "영속성"만 세고 있었습니다.** 05번이 배운
*"착수 전 문서가 센 범위를 믿으면 안 된다"* 가 그대로 반복됐습니다.

## 2. 근거

```bash
# main 클래스 중 대응하는 *Test 가 없는 것
for f in $(find src/main/java/com/serverbe/adapter/out/persistence -name "*Adapter.java"); do
  cls=$(basename "$f" .java)
  [ -z "$(find src/test -name "${cls}Test.java")" ] && echo "테스트 없음: $cls"
done
```

토큰 어댑터를 건드리는 테스트가 아예 없지는 않았습니다 — `BlacklistPerformanceTest`가 블랙리스트
조회 성능을 잽니다. 하지만 그건 **성능 측정이지 동작 검증이 아니었고**, 세션 관리 쪽 10개 메서드는
아무것도 덮지 않았습니다.

## 3. 왜 고쳐야 하는가

**패턴이 이미 저장소 안에 있다는 것이 이 항목의 핵심입니다.** 새로 설계할 것이 없습니다.

- [`UserPersistenceAdapterTest`](../../src/test/java/com/serverbe/adapter/out/persistence/user/UserPersistenceAdapterTest.java) —
  JPA 리포지토리를 목으로 두고 **매퍼 왕복과 예외 번역**을 검증하는 모양
- [`AiTaskRedisAdapterTest`](../../src/test/java/com/serverbe/adapter/out/persistence/task/AiTaskRedisAdapterTest.java) —
  Redis 템플릿을 목으로 두고 **키 조립과 TTL**을 검증하는 모양
- [`RateLimitPersistenceAdapterTest`](../../src/test/java/com/serverbe/adapter/out/persistence/ratelimit/RateLimitPersistenceAdapterTest.java) —
  **Lua 스크립트 호출 인자**를 검증하는 모양. 토큰 어댑터에 가장 가까운 본보기였습니다

세 파일이 각각 다른 종류의 검증을 이미 보여 주고 있고, 빈 곳 셋은 그 셋의 조합입니다.

**토큰 어댑터에서 이 예상이 맞았습니다.** `RefreshTokenSessionAdapterTest`는 세 번째 모양을 거의
그대로 따랐고, 새로 필요했던 것은 하나뿐이었습니다 — 스크립트를 넷 들고 있으므로 **어느 스크립트에
넘겼는지까지** 캡처해야 한다는 것. 키와 인자가 맞아도 다른 스크립트에 넘어가면 전혀 다른 동작이
됩니다.

그리고 **[09번](09-fat-port-token-persistence.md)이 이 항목을 기다리고 있었습니다.** 365줄짜리
어댑터를 둘로 가르는 작업에서 "동작이 안 바뀌었다"를 무엇이 말해 줄 것인가 — **지금은 답이
있습니다.** 분할 전에 쓴 단언이 분할 후에도 그대로 통과합니다.

## 4. 어떻게

### 토큰 어댑터 ✔ — 09번에서 함께 닫았습니다

두 갈래를 나눠서 접근했고, 지금은 어댑터 자체가 둘로 갈려 테스트도 둘입니다.

**키 조립과 단순 연산** — 목 기반 단위 테스트로 충분했습니다. `TokenRedisKeys`가 만드는 키 다섯 개의
모양이 프리픽스 설정과 함께 고정되어 있습니다. **키 모양이 바뀌면 기존 세션이 전부 무효가 되므로**,
이건 JSON 계약(`RestApiResponseJsonContractTest`)과 같은 성격의 고정입니다. 두 테스트가
`TokenRedisKeys`를 **목이 아니라 실제 인스턴스로** 쓰는 이유가 여기 있습니다 — 목으로 두면
고정하려던 것이 사라집니다.

**Lua 스크립트 4개** — `saveToken`·`rotateToken`·`globalLogout`·`deleteToken`. 여기가 어려운
지점이었습니다. 목으로는 **"스크립트에 이런 인자를 넘겼다"까지만** 검증되고, 스크립트가 실제로
원자적으로 동작하는지는 알 수 없습니다. `RateLimitPersistenceAdapterTest`가 딱 그 선까지 가 있습니다.

### 그 선에 대한 판단 — 목으로는 넘을 수 없습니다

기준은 이랬습니다 — **[6번 트러블슈팅](../troubleshooting/06-refresh-token-rotation.md)이 기록한
회전 경합이 목 기반 테스트로 잡히는가?**

**잡히지 않습니다.** 6번이 막으려던 것은 "네 연산 중간에 프로세스가 죽거나 연결이 끊기는 것"이고,
그 실패는 **Redis 서버 안에서** 일어납니다. 목은 `execute(...)`가 불렸다는 사실만 기록할 뿐 그
안에서 무슨 일이 벌어지는지 알지 못합니다. 스크립트 본문을 한 줄 지워도 지금 테스트는 전부
초록입니다.

그래서 현재 덮이는 것과 덮이지 않는 것을 명시합니다.

| 덮인다 | 덮이지 않는다 |
| --- | --- |
| 어느 스크립트에 넘겼는가 (`ArgumentCaptor`로 동일성까지) | 스크립트 본문이 맞는가 |
| KEYS·ARGV의 값과 순서 | 네 연산이 실제로 원자적인가 |
| 키 다섯 개의 모양 | 동시 회전 시 어느 쪽이 이기는가 |
| null·빈 값·장애 시 분기 | 세션 한도 초과 시 실제 축출 결과 |

**오른쪽 열은 실제 Redis가 있어야 합니다.** 임베디드 Redis나 Testcontainers가 필요하고 지금
테스트 환경에는 없으므로, **도입 비용은 별도 항목으로 엽니다.** 이 항목에서 도입하지 않는 이유는
6절에 적은 그대로입니다.

**09번은 이 판단을 받고 진행했습니다.** 어디까지 덮이는지 모르는 채로 포트를 나누지 않았고,
왼쪽 열이 분할 전후로 똑같이 초록인 것을 근거로 삼았습니다.

### `RunningArtPersistenceAdapter` ✔ 11개

Querydsl 동적 쿼리와 페이징이 들어 있어 목 기반으로는 얕게밖에 못 덮습니다. 다만
`RunningArtPageJsonContractTest`와 `PageQueryMapperTest`가 페이징 계약의 일부를 이미 잡고 있으므로,
**그 둘이 덮지 못하는 것**부터 채웠습니다 — 소유자 필터가 실제로 쿼리에 들어가는지, `findAllByIdIn`이
빈 리스트를 받았을 때 어떻게 되는지.

**그리고 문서가 예상하지 못한 것이 하나 더 있었습니다 — `toPageable`입니다.** 페이징 계약이 세
곳으로 나뉘어 있는데, `PageQueryMapperTest`는 **웹 요청 → `PageQuery`** 를,
`RunningArtPageJsonContractTest`는 **응답 JSON 모양**을 봅니다. 그 사이의
**`PageQuery` → `Pageable` → `PageResult`** 구간은 이 어댑터 안에만 있어 **아무도 보지 않고
있었습니다.** 포트가 `Pageable`을 모른다는 결정이 실제로 지켜지는 지점이 바로 거기입니다.

정렬 방향이 뒤집혀도 컴파일되고 예외도 나지 않습니다 — **목록의 순서만 조용히 바뀝니다.**
그래서 두 방향을 모두 고정했습니다.

### `AiTaskPersistenceAdapter` ✔ 10개

가장 작고 가장 값이 컸습니다. `save`가 스프링의 `DataIntegrityViolationException`을
`AiException(DUPLICATE_AI_REQUEST)`으로 번역하는 분기가 **동시 요청 차단의 마지막 방어선**인데
아무것도 검증하지 않고 있었습니다. [06번 문서](06-framework-exception-leak.md)가 이 번역 패턴을
`UserPersistenceAdapter`에도 적용하자고 하므로, **여기 테스트가 그 본보기가 됩니다.**

갱신 분기도 함께 고정했습니다 — **새 엔티티를 만들지 않고 조회한 엔티티에 덮어쓴다**는 것.
`toEntity`로 새로 만들어 저장하면 영속성 컨텍스트가 관리하던 인스턴스와 분리되어 dirty checking이
어긋납니다. `markFailedInBulk`가 `updatedAt`을 직접 채우는 것도 봅니다 — 빠뜨리면 **다음 스윕이
같은 행을 또 집습니다.**

### `RunningArtRedisAdapter` ✔ 6개 — 이 문서에 소절이 없던 것

표에는 있었는데 "어떻게" 절에는 빠져 있었습니다. 실제로 열어 보니 **가장 위험한 단언이 여기
있었습니다.**

```java
.add(geoKey, new Point(lon, lat), id.toString())   // 경도가 먼저다
```

Redis GEO는 **경도-위도** 순으로 받는데 우리가 쓰는 도메인 언어는 늘 **위도-경도**입니다. 누가
"위도가 먼저가 자연스럽다"며 뒤집으면 **컴파일도 테스트도 통과하면서 모든 위치가 조용히
틀립니다** — 서울에서 검색하면 남극이 나오는 종류의 실패이고, 예외가 없어 로그에도 안 남습니다.
`saveLocation`과 `findNearbyIds` 양쪽의 좌표 순서를 `ArgumentCaptor`로 고정했습니다.

**테스트를 쓰다가 알게 된 것 하나** — `geoKey`가 `@Value` **필드 주입**이라 생성자로 넣을 수 없어
`ReflectionTestUtils`로 채워야 했습니다. **그 어색함 자체가 필드 주입의 비용입니다.** 생성자
주입이었다면 테스트가 값을 그냥 넘겼을 것입니다. 프로덕션 코드를 바꾸는 것은 이 항목의 범위가
아니라 사실만 적어 둡니다.

## 5. 재발 방지 — 규칙을 켰습니다, 초록인 범위에서

"모든 클래스에 테스트가 있어야 한다"는 규칙은 두지 않습니다. DTO와 매퍼까지 걸려 소음이 됩니다.
대신 좁은 규칙 하나를 켰습니다.

```
영속성_어댑터는_대응하는_테스트를_가진다
  adapter.out.persistence.. 에서 application.port.out.. 을 구현하는 클래스는
  대응하는 *Test 가 존재해야 한다
```

**범위를 `adapter.out..`에서 `adapter.out.persistence..`로 좁혔습니다.** 원래 적어 둔 규칙 그대로
켜면 지금 즉시 빨간불이기 때문입니다 — 1절에서 드러난 넷이 남아 있습니다.
**빨간 채로 두는 규칙은 아무도 믿지 않게 됩니다.** 규칙은 초록일 때 켜야 의미가 있습니다 —
기존 `LayerDependencyTest`가 "현재 지켜지고 있는 규칙을 고정한다"는 방식으로 도입된 것과
같습니다(커밋 `60943b5`).

**좁힌 것을 숨기지 않는 것이 중요합니다.** 넓히는 일은 [12번](12-test-gaps-outbound-adapters.md)의
종료 조건으로 적어 두었고, 테스트 상수 옆 주석에도 같은 말을 남겼습니다.

> **✔ 12번이 닫히면서 범위가 `adapter.out..` 전체로 넓어졌습니다.** 규칙 이름도
> `아웃바운드_포트_구현체는_대응하는_테스트를_가진다`가 되었고, `@Profile`이 붙은 대역 셋은
> 제외됩니다. **좁게 켜고 넓히는 방식이 실제로 굴러갔습니다** — 규칙이 한 번도 빨간 채로
> 방치되지 않았고, 넓히는 조건이 문서와 코드 양쪽에 적혀 있어 다음 사람이 다시 발견할 필요가
> 없었습니다.

`LayerDependencyTest`에 넣지 않고 **`AdapterTestCoverageTest`를 새로 만들었습니다.** 그 클래스는
`@AnalyzeClasses(DoNotIncludeTests)`라 테스트 클래스를 아예 임포트하지 않는데,
"대응하는 테스트가 있는가"는 **테스트 클래스를 봐야 답할 수 있는 질문**이라 임포트 설정이
정반대입니다. 성격도 계층 의존이 아니라 커버리지입니다.

### 규칙을 세웠다고 믿기 전에 실패시켜 봤습니다

04·05·06·10번의 절차입니다. 범위를 잠깐 `adapter.out`으로 넓혀 빨간 줄을 봤고, **그때 나온
목록이 그대로 12번 문서의 근거가 됐습니다.**

```
Expecting empty but was: ["FakeS3Adapter", "FakeSageMakerAdapter", "JwtTokenProvider",
    "JwtTokenResolver", "MockS3AiOutputAdapter", "S3AiOutputAdapter", "SageMakerAsyncAdapter"]
```

**실패시켜 보는 절차가 여기서는 조사까지 대신했습니다.** 규칙을 한 줄 넓혔다 되돌리는 것만으로
다음 항목의 대상 목록이 나왔습니다.

## 6. 하지 않기로 한 것

- **커버리지 수치를 목표로 삼지 않습니다.** 덮어야 할 것은 줄이 아니라 **분기**이고, 특히
  "실패했을 때 무엇이 일어나는가"입니다.
- **통합 테스트 인프라를 이 항목에서 도입하지 않습니다.** 4절에서 **필요하다는 판단은
  내렸으므로**, 남은 것은 도입 비용을 별도 항목으로 여는 일입니다. 이 항목은 **목으로 덮을 수 있는
  것을 덮고, 덮을 수 없는 것이 무엇인지 명확히 하는 데까지**입니다.
- **컨트롤러와 리졸버의 테스트 공백은 다루지 않습니다.** `RunningArtControllerTest`가 있고 나머지
  컨트롤러에는 없지만, 컨트롤러는 대부분 위임 한 줄이라 우선순위가 낮습니다.
- **Querydsl fluent 체인은 목으로 덮지 않았습니다.** `RunningArtPersistenceAdapter.findAllLocations`와
  `AiTaskPersistenceAdapter.findZombieTasks`가 그렇습니다. 목으로 흉내 내면 **테스트가 프로덕션
  코드의 호출 순서를 그대로 베낀 것**이 되어, 쿼리가 틀려도 초록입니다. 4절이 정한 대로
  **덮을 수 없는 것이 무엇인지 밝히는 데까지**만 했습니다.
- **`@Profile` 페이크 셋에 테스트를 쓰지 않았습니다.** `FakeS3Adapter`·`MockS3AiOutputAdapter`·
  `FakeSageMakerAdapter`는 **그 자체가 테스트 대역**입니다. 페이크에 테스트를 요구하는 규칙은
  소음이 되므로 12번에서 규칙을 넓힐 때 제외 대상입니다.
- **프로덕션 코드를 한 줄도 바꾸지 않았습니다.** `RunningArtRedisAdapter`의 필드 주입처럼 테스트를
  쓰다 눈에 띈 것은 **고치지 않고 적어만 두었습니다.** 09번에서 죽은 catch 분기를 고친 것과 다른
  선택인데, 그건 분기가 명백히 의도와 어긋났고 여기는 취향의 문제이기 때문입니다.

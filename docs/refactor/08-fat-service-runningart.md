# 08. 유스케이스를 여럿 겸하는 서비스 둘

> 상태 · **완료**
> 성격 · 응집도 | 난이도 · 중간 | 선행 항목 · 없음
> 앞선 사례 · SSE 어댑터 3분할. 같은 판단을 서비스 쪽에 적용했습니다.
> **쪼개니 숨어 있던 것 셋이 드러났습니다** — 낡은 주석 둘과 Redis 고아 데이터 둘. 4절을 보세요.

## 1. 무엇이 문제였는가

두 서비스가 성격이 서로 다른 유스케이스를 한 클래스에 모으고 있었습니다.

| 서비스 | 착수 전 줄 수 | 구현하던 인바운드 포트 |
| --- | --- | --- |
| `RunningArtService` | 278 | `GetRunningArtUseCase`, `DeleteRunningArtUseCase`, `UpdateRunningArtUseCase`, `GetNearbyRunningArtUseCase`, `RegisterCompletedArtUseCase` |
| `AiGenerationService` | 318 | `InitiateAiGenerationUseCase`, `GetTaskStatusUseCase` |

## 2. 근거

문서가 처음 적어 둔 명령은 이랬습니다.

```bash
for f in src/main/java/com/serverbe/application/service/*.java; do
  impl=$(grep -o "implements .*{" "$f" | sed 's/implements //;s/ {//')
  [ -n "$impl" ] && echo "$(basename $f) :: $impl"
done
```

**이 명령은 `RunningArtService`를 아예 놓칩니다.** `implements` 절이 다섯 줄에 걸쳐 있어
한 줄을 가정한 `grep`에 걸리지 않기 때문입니다. **이 항목이 가장 크게 다루는 클래스가 근거
목록에서 조용히 빠져 있었습니다.** 줄바꿈까지 세는 명령은 이렇습니다.

```bash
for f in src/main/java/com/serverbe/application/service/*.java; do
  impl=$(tr '\n' ' ' < "$f" | grep -o "class [A-Za-z]* implements [^{]*" | sed 's/.*implements //' | tr -s ' ')
  [ -n "$impl" ] && printf "%-34s :: %s\n" "$(basename $f .java)" "$impl"
done | sort
```

## 3. 왜 고쳤는가 — 개수가 아니라 성격

**"유스케이스 5개는 많다"가 이유가 아니었습니다.** `RunningArtService` 안의 다섯은 셋으로
갈렸습니다.

- **소유권 검증을 공유하는 CRUD** — `getRunningArtById`·`updateRunningArt`·`deleteRunningArt`가
  모두 `findAndVerifyOwner`를 거칩니다. **이 셋은 같이 있어야 합니다.** 검증 로직을 공유하는 것이
  응집의 근거입니다
- **위치 기반 탐색** — `getNearbyArts`는 Redis GEO를 1차 필터로 쓰고 `Flux`를 반환하며, 소유권
  개념이 아예 없습니다(주변 아트는 남의 것도 보입니다)
- **AI 파이프라인의 후반부** — `registerFromPolyline`은 **호출자가 사용자가 아니라 AI 결과 처리
  흐름**입니다

`AiGenerationService`는 더 선명했습니다. `initiateGeneration`은 S3 업로드 → SageMaker 호출 →
보상 트랜잭션을 엮는 리액티브 사가이고, `getTaskStatus`는 DB에서 한 건 읽어 반환하는 동기
메서드였습니다. **한쪽이 실패하면 S3 자원을 되돌려야 하고 다른 쪽이 실패하면 404를 줍니다.**

이 저장소는 이미 같은 판단을 내린 적이 있습니다 — `SseNotificationAdapter` 3분할의 근거는
"세 역할의 방향이 다르다"였지 "클래스가 크다"가 아니었습니다.

## 4. 어떻게 — 한 일

### 절단면은 조사에서 이미 정해져 있었습니다

착수 전 조사가 두 가지를 확인해 줬고, 그것이 작업 전체를 쉽게 만들었습니다.

**하나 — `AiGenerationService`의 절단면은 완벽했습니다.** `getTaskStatus`는 협력자 **하나**
(`TaskQueryPort`)만 쓰고 **private 헬퍼를 하나도 쓰지 않습니다**(본문 9줄). 헬퍼 여덟 개는 전부
사가 쪽이었습니다. 나눌 것이 없어서 나누기 쉬웠습니다.

**둘 — `findAndVerifyOwner`가 경계를 검증해 줬습니다.** 문서는 "쪼갠 뒤 이 메서드가 두 클래스에서
필요해진다면 그건 경계를 잘못 그은 신호"라고 적어 뒀습니다. 이 헬퍼를 쓰는 것은 Get·Update·Delete
셋이고 제안된 절단선이 그 셋을 한 클래스에 남기므로, **`private`인 채로 갇혔습니다.** 신호가
울리지 않은 것이 경계가 맞다는 증거입니다.

### 결과

```
RunningArtService              ← Get · Update · Delete (findAndVerifyOwner 공유)
RunningArtSearchService        ← GetNearbyRunningArtUseCase
RunningArtRegistrationService  ← RegisterCompletedArtUseCase

AiGenerationService            ← InitiateAiGenerationUseCase (리액티브 사가)
AiTaskStatusService            ← GetTaskStatusUseCase (동기 조회)
```

**생성자가 스스로 정리됐습니다.** `RunningArtService`에 명시적 생성자가 있던 유일한 이유는
`ArtSearchPolicy`를 두 개의 primitive로 언패킹하는 것이었는데, **그 정책을 쓰는 것은
`getNearbyArts`뿐**이었습니다. 쪼개고 나니 생성자는 탐색 서비스로 따라갔고 나머지 둘은
`@RequiredArgsConstructor` 한 줄이 되었습니다(import는 있는데 쓰이지 않던 상태였습니다).
**협력자가 갈라진다는 3절의 진단이 코드 모양으로 확인된 지점입니다.**

`AiTaskStatusService`는 목 하나로 테스트됩니다. 사가 쪽은 여덟 개를 세팅해야 합니다.

### 예상 못 한 것 — 쪼개니 드러난 넷

**낡은 주석 둘(고쳤습니다)**

1. **`getNearbyArts`의 `@implNote`** — 문서가 미리 지목한 것입니다. "엄격한 거리순이 요구된다면
   재정렬 로직이 추가되어야 합니다"라고 적혀 있었지만 **코드는 이미 재정렬하고 있었습니다.**
   DB 결과를 `Map`에 담고 Redis가 준 `ids` 순서로 다시 늘어놓는 것이 정확히 그 재정렬입니다.
   하는 일을 안 하는 일처럼 적고 있었습니다.
2. **`deleteAllRunningArtsByUserId`의 `@responsibility`** — "주로 회원 탈퇴 시 호출됩니다"라고
   적혀 있었는데 **사실이 아니었습니다.** 유일한 호출자는 사용자가 직접 여는
   `DELETE /api/v1/running-arts/me`이고, 탈퇴는 이 경로를 타지 않습니다. 문서가 예고하지 않은
   것이고, [03번](03-stale-javadoc-after-listener-split.md)이 다룬 것과 같은 종류입니다.

**Redis GEO 고아 데이터 둘(기록만 했습니다)**

둘 다 같은 모양이고, 문서 6절이 범위를 "동작을 바꾸지 않는다"로 잡았으므로 고치지 않았습니다.
**해당 클래스의 Javadoc에 적어 뒀습니다** — 다음 사람이 발견하고 "실수인가?" 하고 되짚지 않게
하는 것이 이동의 나머지 절반입니다.

1. **등록과 삭제의 규율이 다릅니다.** 삭제 경로는 `afterCommit`으로 Redis 갱신을 커밋 이후로
   미룹니다(README에 근거까지 적혀 있습니다). 그런데 `registerFromPolyline`은 **트랜잭션 안에서
   바로** `saveLocation`을 부릅니다. 커밋이 깨지면 존재하지 않는 아트의 GEO 항목이 남습니다.
   **한 클래스 안에 있을 때는 보이지 않던 불일치입니다** — 이제 두 클래스가 서로 다른 규율을
   쓰는 것이 나란히 보입니다.
2. **탈퇴는 Redis GEO를 지우지 않습니다.** `UserDataCleanupManager`가 DB에서 러닝 아트를 지우고
   `afterCommit`에서는 **리프레시 토큰만** 지웁니다. `removeLocation`은 어디서도 불리지 않습니다.
   `deleteAllRunningArtsByUserId`가 바로 그 일을 하도록 만들어져 있는데 탈퇴가 그것을 우회합니다.
   위 1번 주석이 거짓말이었던 것과 **같은 뿌리**입니다.

### 테스트 — 2개에서 5개로

**내용은 그대로 옮기기만 했습니다.** 19개가 그대로 통과합니다.

| 테스트 | 개수 |
| --- | --- |
| `RunningArtServiceTest` (중첩 `OwnershipTests` 포함) | 5 |
| `RunningArtSearchServiceTest` | 2 |
| `RunningArtRegistrationServiceTest` | 2 |
| `AiGenerationServiceTest` | 7 |
| `AiTaskStatusServiceTest` | 3 |

**배너에 속을 뻔했습니다.** `프롬프트_명령에_지오코딩_좌표와_기본값이_채워진다`는
`// ===== getTaskStatus =====` 배너 **아래**에 있지만 실제로는 `initiateGeneration` 테스트입니다.
구역 주석은 코드가 아니라서 아무도 지켜 주지 않습니다.

**CRUD 테스트의 셋업 주석도 낡았습니다.** "`RunningArtService` 생성자가 값을 즉시 읽으므로
`@InjectMocks`로는 NPE가 납니다"는 `ArtSearchPolicy`가 빠지면서 더는 사실이 아니게 되어
함께 지웠습니다.

**남은 테스트 공백 둘을 기록합니다** — `deleteAllRunningArtsByUserId`와
`compensateAfterExternalSuccess`에는 테스트가 없습니다. [11번](11-test-gaps-persistence-adapters.md) 몫입니다.

### 확인한 것

- **`gradlew build --rerun-tasks` 통과**, 옮긴 테스트 19개 전부 무수정 통과
- **어댑터는 한 줄도 바뀌지 않았습니다.** 컨트롤러 둘과 `AiResultRetrievalService`가 전부
  **인터페이스를 주입**받기 때문입니다. **이것이 포트를 두는 값이고, 이 항목이 안전했던 이유입니다**
- **`ArchUnit` 10개 통과.** `RunningArtSearchService`에 클래스 레벨 `@Transactional`을 일부러 붙여
  실패를 확인하고 되돌렸습니다(4~6번의 절차)
- **컨텍스트 기동은 이번에도 확인하지 못했습니다** — `@Service` 스캔 범위 안에서만 움직였으므로
  배선은 손댈 것이 없지만, **남은 확인**입니다

```
LayerDependencyTest > 트랜잭션_메서드는_리액티브_타입을_반환하지_않는다 FAILED
10 tests completed, 1 failed

Rule 'no methods that are annotated with @Transactional or are declared in classes that are
annotated with @Transactional should have raw return type Mono 또는 Flux' was violated (1 times):
Method <...RunningArtSearchService.getNearbyArts(...)> has raw return type Mono 또는 Flux
```

`areDeclaredInClassesThat`이 **클래스 레벨 애노테이션까지** 본다는 사실이 여기서 실제로
작동했습니다. 리액티브 메서드를 가진 클래스에는 클래스 레벨 트랜잭션을 붙일 수 없습니다.

## 5. 재발 방지 — 규칙 대신 기준

**ArchUnit 규칙을 두지 않았습니다.** "유스케이스를 몇 개까지 구현해도 되는가"에는 옳은 숫자가
없습니다. `UserService`(조회·수정)도, 분해 후의 `RunningArtService`(조회·수정·삭제)도 여럿을
구현한 채 남았고 그게 맞습니다. **숫자로 잡으면 옳은 코드를 막습니다.**

대신 판단 기준을 `application/service/package-info.java`에 적었습니다.

> 한 서비스가 유스케이스를 여럿 구현해도 좋습니다 — **협력자와 실행 모델을 공유할 때만.**
> 주입받는 포트가 갈라지거나, 한쪽만 리액티브거나, 호출자가 사용자와 내부 흐름으로 나뉘면
> 그건 두 서비스입니다.

**규칙이 아니라 기준이어야 하는 항목도 있습니다.** 07번은 구조가 막게 했고, 여기서는 사람이
판단하도록 근거를 남깁니다 — 자동화할 수 없다고 해서 적지 않을 이유는 없습니다.

## 6. 하지 않기로 한 것

- **인바운드 포트를 합치거나 나누지 않았습니다.** 구현 클래스만 다뤘고, 그래서 어댑터가 무수정이었습니다.
- **리액티브 코드를 동기로 바꾸지 않았습니다.** 근거는 [10번](10-reactive-types-in-ports.md).
- **보상 트랜잭션 구조를 손대지 않았습니다.** [2번 트러블슈팅](../troubleshooting/02-s3-orphan-saga-compensation.md)이
  이 코드를 그대로 인용하고 있어, 한 글자라도 바꾸면 그 문서가 낡습니다. 바이트 단위로 옮겼습니다.
- **`AiTaskStatusService`에 `@Transactional`을 붙이지 않았습니다.** 지금 없고, 붙이는 것은
  동작 변경입니다.
- **컨트롤러를 쪼개지 않았습니다.** `RunningArtController`가 블로킹 핸들러 다섯과 리액티브 핸들러
  하나를 섞고 있지만, 이 항목은 서비스 쪽 응집도입니다.

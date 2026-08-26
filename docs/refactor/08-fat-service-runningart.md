# 08. 유스케이스를 여럿 겸하는 서비스 둘

> 상태 · 대기
> 성격 · 응집도 | 난이도 · 중간 | 선행 항목 · 없음
> 앞선 사례 · SSE 어댑터 3분할(커밋 `7813d09`). **같은 판단을 서비스 쪽에 적용하는 항목입니다.**

## 1. 무엇이 문제인가

두 서비스가 성격이 서로 다른 유스케이스를 한 클래스에 모으고 있습니다.

| 서비스 | 줄 수 | 구현하는 인바운드 포트 |
| --- | --- | --- |
| [`RunningArtService`](../../src/main/java/com/serverbe/application/service/RunningArtService.java) | 273 | `GetRunningArtUseCase`, `DeleteRunningArtUseCase`, `UpdateRunningArtUseCase`, `GetNearbyRunningArtUseCase`, `RegisterCompletedArtUseCase` |
| [`AiGenerationService`](../../src/main/java/com/serverbe/application/service/AiGenerationService.java) | 317 | `InitiateAiGenerationUseCase`, `GetTaskStatusUseCase` |

## 2. 근거

```bash
# 서비스별로 구현하는 유스케이스를 나열한다
for f in src/main/java/com/serverbe/application/service/*.java; do
  impl=$(grep -o "implements .*{" "$f" | sed 's/implements //;s/ {//')
  [ -n "$impl" ] && echo "$(basename $f) :: $impl"
done
```

나머지 서비스는 대부분 유스케이스 하나를 구현합니다. `UserService`가 둘(`GetUserUseCase`,
`UpdateUserUseCase`)인데 그 둘은 같은 대상에 대한 조회·수정이라 자연스럽습니다.

## 3. 왜 고쳐야 하는가 — 개수가 아니라 성격이 문제입니다

**"유스케이스 5개는 많다"가 이유가 아닙니다.** `RunningArtService` 안의 다섯 개는 셋으로 갈립니다.

- **소유권 검증을 공유하는 CRUD** — `getRunningArtById`·`updateRunningArt`·`deleteRunningArt`는
  모두 `findAndVerifyOwner`를 거칩니다. **이 셋은 같이 있어야 합니다.** 검증 로직을 공유하는 것이
  응집의 근거입니다
- **위치 기반 탐색** — `getNearbyArts`는 Redis GEO 인덱스를 1차 필터로 쓰고 `Flux`를 반환하는
  리액티브 코드입니다. `RunningArtRedisPort`에 의존하고 소유권 검증과 아무 관계가 없습니다
- **AI 파이프라인의 후반부** — `registerFromPolyline`은 폴리라인에서 메타데이터를 뽑아 아트를
  만듭니다. **호출자가 사용자가 아니라 AI 결과 처리 흐름입니다**

세 갈래는 **의존하는 포트도, 호출하는 쪽도, 실행 모델(동기/리액티브)도 다릅니다.** 지금은 한
클래스가 그 셋을 모두 알아야 하고, 생성자가 네 개의 협력자를 받습니다.

`AiGenerationService`는 더 선명합니다. `initiateGeneration`은 **S3 업로드 → SageMaker 호출 →
보상 트랜잭션**까지 다루는 리액티브 사가이고(이 클래스 줄 수의 대부분), `getTaskStatus`는
**DB에서 한 건 읽어 반환하는 동기 메서드**입니다. 한쪽이 실패하면 S3 객체를 지워야 하는 흐름과,
한쪽이 실패하면 404를 주는 흐름이 같은 클래스에 있습니다.

**이 저장소는 이미 같은 판단을 내린 적이 있습니다.** `SseNotificationAdapter` 하나가 emitter
레지스트리·Redis 발행·Redis 수신을 겸하고 있었고, 그것을 방향에 맞게 셋으로 갈랐습니다. 근거는
"세 역할의 방향이 다르다"였지 "클래스가 크다"가 아니었습니다.

## 4. 어떻게

**`RunningArtService`**

```
RunningArtService          ← 소유권 검증을 공유하는 CRUD (Get / Update / Delete)
RunningArtSearchService    ← GetNearbyRunningArtUseCase
RunningArtRegistrationService ← RegisterCompletedArtUseCase
```

`deleteAllRunningArtsByUserId`(`DeleteRunningArtUseCase`의 일부, 탈퇴 시 호출)는 CRUD 쪽에
남습니다. `findAndVerifyOwner`가 `private`이므로 **자연스럽게 CRUD 서비스에 갇힙니다** —
쪼갠 뒤 이 메서드가 두 클래스에서 필요해진다면 그건 경계를 잘못 그은 신호입니다.

**`AiGenerationService`**

```
AiGenerationService   ← InitiateAiGenerationUseCase (리액티브 사가)
AiTaskStatusService   ← GetTaskStatusUseCase (동기 조회)
```

**둘 다 인바운드 포트는 손대지 않습니다.** 컨트롤러는 이미 유스케이스 인터페이스를 주입받고
있으므로 **구현 클래스를 나눠도 컨트롤러 코드는 그대로**입니다. 이것이 포트를 두는 값이고, 이
항목이 안전한 이유입니다.

**곁다리로 함께 고칠 것** — `getNearbyArts`의 `@implNote`가 이렇게 적혀 있습니다.

> "만약 클라이언트에게 엄격한 거리순 반환이 요구된다면, DB 조회 이후 스트림 내에서 반환된 리스트를
> 재정렬하는 로직이 추가되어야 합니다."

**코드는 이미 재정렬하고 있습니다.** Redis가 돌려준 `ids` 순서로 `artMap`을 훑어 다시 늘어놓는
것이 정확히 그 재정렬입니다. [03번 문서](03-stale-javadoc-after-listener-split.md)와 같은 종류의
낡은 주석이므로 여기서 함께 지웁니다.

## 5. 재발 방지

ArchUnit 규칙을 두지 않습니다. "유스케이스를 몇 개까지 구현해도 되는가"에는 옳은 숫자가 없고,
`UserService`처럼 둘이 자연스러운 경우도 있습니다. **숫자로 잡으면 옳은 코드를 막습니다.**

대신 판단 기준을 남깁니다.

> 한 서비스가 유스케이스를 여럿 구현해도 좋습니다 — **협력자와 실행 모델을 공유할 때만.**
> 주입받는 포트가 갈라지거나, 한쪽만 리액티브거나, 호출자가 사용자와 내부 흐름으로 나뉘면
> 그건 두 서비스입니다.

## 6. 하지 않기로 한 것

- **인바운드 포트를 합치거나 나누지 않습니다.** 지금 포트 분할(`Get`/`Update`/`Delete`/`Nearby`/
  `Register`)은 적절합니다. 이 항목은 **구현 클래스**만 다룹니다.
- **`RunningArtService`의 리액티브 코드를 동기로 바꾸지 않습니다.** `getNearbyArts`가 `Flux`를
  반환하는 것은 Redis GEO 조회가 리액티브이기 때문이고, 그 판단의 근거는
  [10번 문서](10-reactive-types-in-ports.md)에 있습니다.
- **`AiGenerationService`의 보상 트랜잭션 구조를 손대지 않습니다.** S3 고아 객체 보상은
  [2번 트러블슈팅](../troubleshooting/02-s3-orphan-saga-compensation.md)의 결론이고 그대로 옮겨
  갑니다.

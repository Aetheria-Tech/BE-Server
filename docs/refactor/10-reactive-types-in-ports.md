# 10. 포트가 `Mono`·`Flux`를 노출한다

> 상태 · **결정 — 고치지 않습니다**
> 성격 · 판단 기록 | 선행 항목 · 없음
> 반드시 함께 읽기 · [02번 — `@Transactional`이 `Mono` 위에서 무효화된다](02-transactional-on-mono.md)

이 디렉터리의 다른 문서가 "할 일"이라면 이것은 **하지 않기로 한 일**의 기록입니다.
[12번 Kafka 문서](../troubleshooting/12-why-not-kafka.md)와 같은 성격입니다.

"헥사고날인데 왜 포트에 Reactor 타입이 있는가"는 정당한 질문이고, 그 답을 근거와 함께 남깁니다.

## 1. 현황

포트 8개가 시그니처에 Reactor 타입을 씁니다.

| 방향 | 포트 |
| --- | --- |
| 인바운드 | `GetNearbyRunningArtUseCase` · `InitiateAiGenerationUseCase` · `GeocodeAddressUseCase` · `LoginUseCase` · `WithdrawUseCase` |
| 아웃바운드 | `RunningArtRedisPort` · `GeocodePort` · `OAuthClientPort` |

```bash
grep -rln "Mono<\|Flux<" src/main/java/com/serverbe/application/port | sort
```

## 2. 먼저 짚을 것 — 02번과 혼동하면 안 됩니다

[02번 문서](02-transactional-on-mono.md)가 다루는 것은 **선언적 트랜잭션을 리액티브 메서드에
얹은 것**입니다. 리액티브 타입 자체가 아닙니다.

두 항목이 같은 파일(`WithdrawService`)을 가리키기 때문에 "결국 Reactor가 문제 아닌가"로 읽힐 수
있습니다. 아닙니다. **`Mono`를 걷어내도 02번은 사라지지 않고**(동기 메서드에 잘못된 트랜잭션
경계를 잡을 수 있습니다), **02번을 고쳐도 `Mono`는 남습니다.** 원인이 다릅니다.

## 3. 왜 남기는가

### Reactor는 프레임워크가 아니라 라이브러리입니다

`LayerDependencyTest`가 지키는 것은 **의존 방향**입니다. 도메인은 JDK와 Lombok만 알고, 애플리케이션은
어댑터와 인프라를 모릅니다. Reactor는 그 어느 선도 넘지 않습니다 — **도메인에는 한 줄도 없고**,
포트와 서비스에만 있습니다.

```bash
# 도메인에 Reactor가 없다는 것 — 결과가 비어야 한다
grep -rn "reactor" src/main/java/com/serverbe/domain
```

Spring Security 타입과 Spring Data 타입을 포트에서 걷어낸 것은(커밋 `8345984`, `ff9c2f5`)
그것들이 **특정 프레임워크의 실행 환경을 요구**했기 때문입니다. `Mono`는 그렇지 않습니다.

### 인바운드 포트의 `Mono`는 계약입니다

`InitiateAiGenerationUseCase.initiateGeneration`이 `Mono<String>`을 반환한다는 것은
**"이 유스케이스는 논블로킹으로 호출해도 된다"** 는 선언입니다. 안에서 S3 업로드와 SageMaker 호출이
일어나므로, 호출자가 그것을 알아야 스레드를 어떻게 쓸지 정할 수 있습니다.

실제로 `AiGenerationController`와 `AuthController`는 `subscribeOn(Schedulers.boundedElastic())`을
붙여 구독합니다. **반환 타입을 `String`으로 바꾸면 컨트롤러는 그 결정을 내릴 근거를 잃습니다** —
겉보기에 즉시 반환되는 메서드가 사실은 네트워크 두 번을 기다린다는 것을 알 방법이 없어집니다.

같은 이유로 `getTaskStatus`(DB 한 건 조회)는 `Mono`가 아닙니다. **타입이 실행 특성을 정직하게
말하고 있습니다.**

### 지웠을 때 얻는 것이 없습니다

포트에서 `Mono`를 걷어내려면 두 가지 중 하나를 해야 합니다.

- **서비스 안에서 `block()` 한다** — 리액티브를 쓰는 이유가 사라집니다. 그리고
  [1번 트러블슈팅](../troubleshooting/01-webflux-blocking-io.md)이 정확히 그 실수의 기록입니다
- **`CompletableFuture` 같은 표준 타입으로 바꾼다** — 어댑터가 WebClient(Reactor)를 쓰므로 경계마다
  변환이 생깁니다. **의존이 사라지는 게 아니라 변환 비용만 생깁니다**

## 4. 그럼에도 갈래는 하나 있습니다 — 아웃바운드 쪽

인바운드와 아웃바운드는 사정이 다릅니다.

인바운드 포트의 `Mono`는 **애플리케이션이 호출자에게 하는 약속**입니다. 위에서 적은 근거가 그대로
적용됩니다.

아웃바운드 포트의 `Mono`는 **어댑터 사정이 새어 나온 것에 가깝습니다.** `GeocodePort`가 `Mono`를
반환하는 이유는 카카오 어댑터가 WebClient를 쓰기 때문이고, 만약 구현이 블로킹 HTTP 클라이언트로
바뀌면 그 포트는 `Mono`일 이유가 없습니다. 지금은 세 포트 모두 **WebClient 또는 Reactive Redis**
구현뿐이라 드러나지 않습니다.

**재검토 트리거** — 아웃바운드 포트 셋 중 하나라도 **리액티브가 아닌 구현체가 생기면** 그때 다시
봅니다. 그 시점에는 `Mono`가 계약이 아니라 우연이라는 게 드러납니다.

## 5. 요약

| 질문 | 답 |
| --- | --- |
| 도메인이 Reactor를 아는가 | **아니오.** 한 줄도 없습니다 |
| 인바운드 포트의 `Mono`를 지울 것인가 | **아니오.** 실행 특성을 말하는 계약입니다 |
| 아웃바운드 포트의 `Mono`는 | **조건부.** 리액티브가 아닌 구현체가 생기면 재검토합니다 |
| 02번과 같은 문제인가 | **아니오.** 문제는 `Mono`가 아니라 거기 얹은 스레드 바인딩 트랜잭션입니다 |

여기서 나오는 규칙 한 줄 — **타입은 실행 특성을 정직하게 말해야 합니다.** 네트워크를 두 번 타는
메서드가 `String`을 반환하면 호출자는 속습니다. 추상화를 위해 그 정직함을 버리는 것은 남는 장사가
아닙니다.

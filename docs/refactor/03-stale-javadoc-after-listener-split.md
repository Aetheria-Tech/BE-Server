# 03. 리스너 분리가 남긴 낡은 주석

> 상태 · **완료**
> 성격 · 문서 | 난이도 · 낮음 | 선행 항목 · 없음
> **착수해 보니 다섯 번째가 있었습니다** — 2절 마지막 행. 틀린 주석 옆에 그것을 그럴듯하게 만들던 미사용 import가 남아 있었습니다.
> 배경 · [SQS 메시지 흐름](../architecture/sqs-message-flow.md) · [12번 §7 — 무엇을 정리했고 무엇을 남겼나](../troubleshooting/12-why-not-kafka.md#7-남은-과제--무엇을-정리했고-무엇을-남겼나)

## 1. 무엇이 문제인가

`AiNotificationSqsListener`를 **전송 번역(리스너)** 과 **처리 규칙(`AiNotificationService`)** 으로
가르면서 트랜잭션을 여는 클래스가 바뀌었습니다. 그런데 그 사실을 설명하던 주석들은 여전히
**"SQS 리스너"** 를 트랜잭션 소유자로 지목하고 있습니다.

한 건은 성격이 다릅니다. `LoginService`의 주석은 낡은 것이 아니라 **처음부터 사실이 아닙니다.**

## 2. 근거

```bash
grep -rn "리스너" src/main/java/com/serverbe/application --include=*.java
grep -n "Transactional" src/main/java/com/serverbe/application/service/LoginService.java
```

두 번째 명령이 **문서에 적지 않았던 것 하나를 더 내놓았습니다.** 아래 표의 마지막 행입니다.

| 위치 | 지금 적혀 있는 것 | 실제 |
| --- | --- | --- |
| `AiResultRetrievalService` 클래스 javadoc | "이 서비스의 유일한 실질 호출자인 **SQS 리스너**는 비관적 락 유지를 위해 `@Transactional`로 감싸여 있습니다" | 호출자는 `AiNotificationService`. 리스너에는 `@Transactional`이 없습니다 |
| 같은 javadoc | "`handleSuccess`의 트랜잭션은 **리스너의 트랜잭션에 합류**합니다" | 합류 대상은 `AiNotificationService.handleNotification`의 트랜잭션 |
| `AiResultRetrievalService#handleFailure` 주변 주석 | "바깥(**리스너**) 트랜잭션은 이미 rollback-only로" | 같음 |
| `RunningArtService#registerFromPolyline`(지금은 `RunningArtRegistrationService`) | `.block(); // SQS 리스너 워커 스레드이므로` | 스레드는 여전히 리스너 워커가 맞지만, 호출 경로에 서비스가 하나 끼었습니다 |
| `LoginService.login` javadoc | "서비스 레이어에서의 `@Transactional`은 헬퍼 컴포넌트 내부로 전파되어 원자성을 보장받습니다" | **`LoginService`에는 `@Transactional`이 없습니다** |
| `LoginService` import 목록 | `org.springframework.transaction.annotation.Transactional` | **어디에도 쓰이지 않습니다.** 착수 후에 찾았습니다 |

마지막 행이 이 항목에서 가장 흥미로운 부분입니다. 그 import는 **틀린 주석을 그럴듯하게 만들어 온
화석**입니다. 파일 맨 위에 `Transactional`이 보이니 "이 서비스에 트랜잭션이 있다"는 문장이 읽는
사람에게 한 번 더 확인받는 것처럼 보입니다. **주석만 고치고 import를 남겼다면 다음 사람이 다시
속았을 것입니다.**

## 3. 왜 고쳐야 하는가

**설명이 코드보다 오래 살기 때문입니다.**

앞의 네 건은 트랜잭션이 실제로 존재하고 동작도 그대로입니다. 바뀐 것은 **그것을 여는 클래스의
이름**뿐입니다. 그래서 눈에 띄지 않고, 그래서 오래 남습니다. 하지만 이 주석들의 존재 이유는
"어디를 열어 보면 되는지 알려 주는 것"인데, 지금은 **없는 곳을 가리키고 있습니다.**

`LoginService`의 것은 더 나쁩니다. 그 주석은 **있지도 않은 보장을 약속합니다.**
`userDataSyncManager.syncUserByOAuth`(JPA 트랜잭션)와 `authSessionManager.saveSession`(Redis)은
서로 다른 경계이고, 사이에 원자성이 없습니다. 회원 정보 동기화가 커밋된 뒤 세션 저장이 실패하면
사용자는 가입은 되었으나 로그인은 안 된 상태가 됩니다. **그 위험을 감수한다는 판단은 있을 수
있지만, "원자성을 보장받습니다"라고 적어 두는 것은 다릅니다.**

한 가지 더. [아키텍처 문서](../architecture/sqs-message-flow.md)는 이미 옳게 적혀 있습니다 —
"트랜잭션을 여는 것은 리스너가 아니라 `AiNotificationService.handleNotification`입니다". 그런데 그
문단은 근거로 *"이 점은 서비스 클래스의 javadoc에도 명시되어 있습니다"*라고 가리킵니다. **가리키는
그 javadoc이 낡은 쪽이었습니다.** 옳은 문서가 틀린 주석을 증거로 들고 있었던 셈입니다.

## 4. 어떻게 — 한 일

낡은 네 건과 틀린 한 건을 다르게 다뤘습니다.

### 낡은 네 건 — 이름 대신 **역할**로 가리킨다

특정 클래스를 이름으로 지목하는 대신 **역할로 가리킵니다.** "SQS 리스너가 `@Transactional`로
감싸고 있다" 대신 "이 유스케이스를 호출하는 쪽이 비관적 락 유지를 위해 트랜잭션을 열고 있다"로
쓰면, 호출자가 다시 바뀌어도 문장은 그대로 참입니다. 정확한 클래스를 알아야 하는 독자를 위해서는
`{@link}`를 씁니다 — **링크는 클래스가 이동해도 IDE가 따라가지만 산문은 따라가지 않습니다.**

[`AiResultRetrievalService`](../../src/main/java/com/serverbe/application/service/AiResultRetrievalService.java)의
세 문장이 이렇게 바뀌었습니다.

```
- 이 서비스의 유일한 실질 호출자인 SQS 리스너는 … {@code @Transactional}로 감싸여 있습니다.
+ 이 유스케이스를 호출하는 쪽이 비관적 락을 유지하기 위해 트랜잭션을 열어 둡니다
+ (지금은 {@link AiNotificationService#handleNotification}).
```

**`.block()` 주석은 성격이 조금 달랐습니다.** "SQS 리스너 워커 스레드이므로"는 지금도 사실입니다 —
틀린 것은 문장이 아니라, **왜 안전한지의 근거를 특정 전송 수단에 묶어 둔 것**이었습니다. 큐가
바뀌면 근거가 통째로 흔들립니다. 그래서 인라인 주석은 짧게 남기고
([`RunningArtRegistrationService`](../../src/main/java/com/serverbe/application/service/RunningArtRegistrationService.java) —
[08번](08-fat-service-runningart.md)에서 `RunningArtService`에서 갈라져 나왔습니다),
근거는 메서드 javadoc `@implNote`로 올렸습니다 — 진짜 전제는 SQS가 아니라 **"이벤트 루프가 아닌
블로킹 워커 스레드에서 호출된다"** 는 것이고, 그 전제가 깨지는 조건까지 함께 적었습니다.
**지우지 않고 올린 이유는 6절에 있습니다.**

### `LoginService`의 한 건 — 주석을 사실에 맞춘다

주석을 다음 세 가지로 바꿨습니다.

- 이 메서드에는 **트랜잭션 경계가 없다**는 것
- 회원 동기화는 `syncUserByOAuth`의 트랜잭션 안에서, 세션 저장은 Redis에서 각각 독립적으로
  일어난다는 것
- 따라서 둘 사이는 **원자적이지 않으며**, 세션 저장 실패 시 사용자는 재로그인으로 복구된다는 것

**세 번째는 확인하고 적었습니다.** 착수 전에는 "확인되지 않으면 남은 과제로 명시한다"고 단서를
달아 두었는데, 확인됐으므로 사실로 적습니다. 근거는
[`UserDataSyncManager.syncUserByOAuth`](../../src/main/java/com/serverbe/application/service/helper/UserDataSyncManager.java)가
`findByOauthId` → 있으면 갱신, 없으면 등록인 **멱등한 upsert**라는 것입니다. 세션 저장이 실패해
커밋된 채 남은 사용자 행은 재로그인 시 그대로 재사용되고, 토큰은 새로 발급되며, 저장되지 못한
리프레시 토큰은 Redis 어디에도 남지 않아 잔여물이 없습니다.

그리고 **미사용 import를 함께 지웠습니다**(2절 마지막 행). 주석만 고치는 것으로는 부족했습니다.

**원자성이 없어도 되는가**라는 질문에는 이 항목이 답하지 않습니다 — 6절을 보세요.

## 5. 재발 방지

자동 규칙을 두지 않습니다. "주석이 사실인가"는 정적으로 검사할 수 없습니다.

대신 규칙을 문장으로 남깁니다. 이 저장소는 javadoc에 **왜 그렇게 했는지**를 길게 적는 관례가
있고 그것이 이 코드베이스의 강점이므로, 관례를 줄이는 방향이 아니라 **덜 낡는 방향**으로 씁니다.

> **다른 클래스를 산문으로 지목하지 않습니다.** 협력 관계를 설명해야 하면 `{@link}`로 걸고,
> 문장은 **역할**로 씁니다("호출하는 쪽", "이 유스케이스를 여는 쪽"). 클래스 이름을 문장에 박으면
> 그 클래스가 움직이는 날 주석이 조용히 거짓말이 됩니다.

**판단 결과 — [`docs/troubleshooting/README.md`](../troubleshooting/README.md)에는 넣지 않습니다.**
그 "관통하는 규칙" 절은 **여러 장애 문서에 반복 관찰된 판단**을 문서 번호와 함께 적는 자리입니다.
이 규칙은 장애 판단이 아니라 **주석 작성 규약**이고, 인용할 트러블슈팅 번호가 없습니다. 그리고 같은
내용이 이미 [`refactor/README.md`](README.md)에 *"설명이 코드보다 오래 산다 — 주석이 다른 클래스를
이름으로 지목하면, 그 클래스가 움직이는 날 주석은 조용히 거짓말이 됩니다. (03, 08)"* 로 서 있습니다.
**한 규칙은 한 곳에 둡니다** — 두 곳에 두면 둘이 갈라지는 날 어느 쪽이 진짜인지 알 수 없게 되고,
그것이 바로 이 항목이 다루는 실패 방식입니다. 다음 사람이 같은 질문을 다시 열지 않도록 결론을
여기 남깁니다.

## 6. 하지 않기로 한 것

- **`LoginService`의 트랜잭션 경계를 이 항목에서 바꾸지 않습니다.** 소셜 로그인의 원자성을 어디까지
  보장할 것인가는 별도 판단이고, 근거(재로그인으로 복구 가능한가, 세션 저장 실패율이 얼마인가)를
  모으고 나서 결정해야 합니다. 이 항목은 **주석을 사실에 맞추는 것**까지입니다.
  **두 근거 중 하나는 이번에 확보했습니다** — 재로그인으로 복구됩니다(4절). 나머지 하나인 실패율은
  운영 지표가 있어야 하므로, 새 항목을 여는 것은 그 숫자를 본 뒤로 미룹니다. 지금 아는 것으로는
  "복구 가능한 실패"이고, 그렇다면 경계를 넓히는 비용이 이득보다 클 수 있습니다.
- **주석을 줄이지 않습니다.** 낡을 수 있다는 이유로 설명을 지우면, 다음 사람은 왜 그렇게 되어
  있는지를 코드에서 역추적해야 합니다. 그쪽이 훨씬 비쌉니다.

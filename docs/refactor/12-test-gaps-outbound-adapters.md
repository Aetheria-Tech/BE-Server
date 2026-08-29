# 12. 영속성 밖의 아웃바운드 어댑터 테스트 공백

> 상태 · **완료** (커밋 전, 워킹 트리)
> 성격 · 테스트 | 난이도 · 중간 | 선행 항목 · 없음
> **[11번](11-test-gaps-persistence-adapters.md)이 자기 범위를 잘못 세어 남긴 항목이었습니다.**
> 규칙은 이미 켜져 있었고 범위만 좁았습니다 — **넓혔고, 초록입니다.**
> **읽다가 셋을 발견했습니다** — 거짓말하는 javadoc 하나와 도달 불가 catch 둘. 6절을 보세요.

## 1. 무엇이 문제인가

11번은 "영속성 어댑터"의 테스트 공백을 닫았습니다. 그런데 그 문서가 켜자고 한 규칙은
`adapter.out..` **전체**를 덮는 것이었고, 실제로 세어 보니 **테스트 없는 아웃바운드 포트 구현체는
셋이 아니라 열이었습니다.**

| 클래스 | 줄 수 | 무엇을 하는가 | 테스트 |
| --- | --- | --- | --- |
| `JwtTokenResolver` | 226 | **저장소에서 가장 컸던 무테스트 클래스.** 토큰 검증·클레임 해석 | ✔ 14개 |
| `S3AiOutputAdapter` | 151 | AI 결과물 다운로드 | ✔ 8개 |
| `JwtTokenProvider` | 137 | 액세스·리프레시 토큰 발급 | ✔ 5개 |
| `SageMakerAsyncAdapter` | 72 | 비동기 추론 호출 | ✔ 4개 |

**11번이 이것을 놓친 이유가 분명합니다.** [04번](04-outbound-adapter-location.md)이
`JwtTokenProvider`·`JwtTokenResolver`를 `infrastructure`에서 `adapter.out.security`로 옮겼는데,
11번은 그 뒤에도 **"영속성 어댑터"만 세고 있었습니다.** 항목이 항목을 깎는 이 백로그에서
**앞 항목이 뒤 항목의 대상을 늘린** 첫 사례입니다.

## 2. 근거

11번이 켠 규칙의 범위를 한 줄 넓히면 그대로 나옵니다.

```java
// AdapterTestCoverageTest
private static final String 대상_패키지 = "com.serverbe.adapter.out";  // .persistence 를 뗀다
```

```
Expecting empty but was: ["FakeS3Adapter", "FakeSageMakerAdapter", "JwtTokenProvider",
    "JwtTokenResolver", "MockS3AiOutputAdapter", "S3AiOutputAdapter", "SageMakerAsyncAdapter"]
```

## 3. 왜 고쳐야 하는가

**`JwtTokenResolver`가 가장 급합니다.** 226줄이 전부 "이 토큰을 믿어도 되는가"를 판단하는 코드이고,
**틀렸을 때의 실패 방향이 인증 우회입니다.** 만료 검사·서명 검증·클레임 해석 중 하나가 조용히
느슨해져도 정상 요청은 그대로 통과하므로 **증상이 없습니다.**

`JwtAuthenticationFilterTest`가 있지만 그건 **필터가 조립하는 인증 객체의 모양**을 볼 뿐,
`TokenResolver`를 목으로 두고 있어 **판단 자체는 아무도 보지 않습니다.**

나머지 셋은 외부 시스템과의 대화입니다. `SageMakerAsyncAdapter`는 `AiGenerationService`가,
`S3AiOutputAdapter`는 `AiResultRetrievalService`와 `AiTaskResourceCleaner`가 씁니다 —
**[2번 트러블슈팅](../troubleshooting/02-s3-orphan-saga-compensation.md)이 기록한 보상 흐름의 양
끝입니다.** 그 문서는 서비스 쪽 보상 로직을 근거로 들고 있고, **어댑터가 실제로 무엇을 보내고
실패를 어떻게 옮기는지는 지금 아무도 검증하지 않습니다.**

## 4. 어떻게 했는가

패턴은 11번과 같았습니다 — 저장소 안에 이미 있었습니다.

**`JwtTokenProvider`·`JwtTokenResolver`** — 목이 거의 필요 없었습니다. 실제 `JwtKeyManager`로 키를
만들고 `EncryptPort`만 항등 함수로 두었습니다(암호화 자체는 `AesGcmEncryptorTest`가 봅니다).

고정한 것 중 둘이 특히 중요합니다.

- **액세스 토큰의 `subject`가 평문이 아니라는 것.** JWT 페이로드는 서명될 뿐 **암호화되지
  않습니다** — 누구나 Base64 디코드로 읽습니다. 암호화가 빠져도 토큰은 멀쩡히 동작하므로
  **사용자 ID와 권한이 노출된 채로 아무 증상이 없습니다.**
- **만료된 토큰에서도 페이로드를 꺼낼 수 있다는 것.** 재발급은 만료된 액세스 토큰을 들고 오는
  흐름이라, 만료를 이유로 추출까지 막으면 **재발급이 전부 실패합니다.** 만료 검사와 페이로드
  추출은 서로 다른 정책이고, 그 사실이 테스트로 고정되었습니다.

**`S3AiOutputAdapter`** — 예외 다섯 갈래가 어디로 모이는지를 봅니다. **`NoSuchKeyException`만
예외가 아닙니다** — 결과물이 아직 없다는 것은 "추론 진행 중"이라는 정상 신호이고, 이것이 예외로
바뀌면 **폴링 요청이 전부 500으로 떨어집니다.** 반대로 `deleteOutput`은 **무슨 예외가 나도 밖으로
던지지 않습니다**(장애 격리) — 삭제 실패가 알림 파이프라인을 끊으면 데이터는 저장됐는데 사용자만
결과를 못 받습니다.

**`SageMakerAsyncAdapter`** — `inferenceId`에 `taskId`를 싣는 것이 핵심입니다. SageMaker는 이 값을
**실패 알림에도** 되돌려주는데 실패 알림에는 결과물 경로가 없을 수 있어, 이 값이 빠지면
**추론 실패가 DB에 기록되지 못한 채 메시지가 DLQ로 빠집니다.** 사용자 쪽에서는 작업이 영원히
"진행 중"으로 남습니다.

## 5. 재발 방지 — 규칙의 범위를 넓혔습니다

**새 규칙을 만들지 않았습니다.** 11번이 켠 `AdapterTestCoverageTest`의 `대상_패키지` 상수를
`adapter.out.persistence` → `adapter.out`으로 넓힌 것이 전부입니다. **이 항목의 종료 조건이
그것이었습니다.**

넓히면서 **`@Profile` 페이크 셋을 제외했습니다.**

| 클래스 | 줄 수 |
| --- | --- |
| `FakeS3Adapter` | 49 |
| `MockS3AiOutputAdapter` | 45 |
| `FakeSageMakerAdapter` | 30 |

**그것들 자체가 테스트 대역이기 때문입니다.** 페이크에 테스트를 요구하는 규칙은 "가짜가 가짜인지
확인하는 테스트"를 만들게 하고, 그건 정확히 11번이 피하려던 소음입니다. 제외는 **애노테이션으로**
했습니다 — `@Profile`이 붙어 있으면 상용 경로가 아니라는 **선언**이고, 이름 규칙(`Fake*`·`Mock*`)은
누구나 다르게 지을 수 있어 규칙이 이름 짓기 취향에 끌려다니게 됩니다.

### 규칙을 세웠다고 믿기 전에 실패시켜 봤습니다

04·05·06·10·11번의 절차입니다. **둘을 따로 깨뜨렸습니다** — 넓힌 범위와 새로 넣은 제외가 각각
일하고 있는지는 서로 다른 질문이기 때문입니다.

```
# 1. @Profile 제외를 잠깐 뺐을 때 — 제외가 실제로 일하고 있다
Expecting empty but was: ["FakeS3Adapter", "FakeSageMakerAdapter", "MockS3AiOutputAdapter"]

# 2. 새 테스트 하나의 클래스 이름을 잠깐 바꿨을 때 — 넓힌 범위가 실제로 감시 중이다
Expecting empty but was: ["SageMakerAsyncAdapter"]
```

**둘 다 정확히 기대한 것만 나왔습니다.** 제외를 빼면 페이크 셋만, 이름을 바꾸면 그 어댑터만
걸립니다 — 규칙이 넓게 잡아 놓고 아무거나 잡는 것이 아니라는 뜻입니다.

## 6. 테스트를 씌우다 드러난 것 셋

전부 `JwtTokenResolver`에서 나왔습니다. **하나만 고치고 둘은 기록합니다.**

### 없는 보안 검사를 설명하던 javadoc ✔ 고쳤습니다

`validateRefreshToken`의 주석은 이렇게 적혀 있었습니다.

> 리프레시 토큰은 정보를 담지 않는 무작위 문자열이므로, **설정된 길이와 일치하는지를 우선적으로
> 확인합니다.**

**그런 검사는 코드에 없습니다.** `StringUtils.hasText` 한 줄이 전부입니다. 길이 검사가 있다고
믿고 읽으면 **이 메서드가 통과시킨 토큰은 형식이 검증된 것**이라고 오해하게 됩니다. 실제 유효성은
저장소 대조(`RefreshTokenSessionPort.existsRefreshToken`)와 블랙리스트가 판정합니다.

[03번](03-stale-javadoc-after-listener-split.md)이 다룬 것과 같은 성격이지만 **더 나쁩니다** —
03번의 주석은 없는 클래스를 가리켰고, 이 주석은 **없는 보안 검사가 있다고 말합니다.** 사실에
맞추고, 실제 판정이 어디서 일어나는지를 함께 적었습니다.

### 도달 불가 catch 둘 — 기록만 합니다

```java
public Long getIdFromToken(String accessToken) {
    try { return resolvePayload(accessToken).userId(); }
    catch (NumberFormatException e) { throw new AuthException(...); }  // 도달하지 않는다
}
```

내부 `extractId`가 **이미** `NumberFormatException`을 `AuthException`으로 바꿔 던지므로 바깥
catch에는 아무것도 오지 않습니다. `getRoleFromToken`의 `IllegalArgumentException` 분기도 같습니다.

**[09번](09-fat-port-token-persistence.md)의 죽은 catch와는 다르게 처리했습니다.** 그쪽은
`jakarta.persistence`와 `org.springframework.dao`를 **혼동한 명백한 실수**여서 의도한 동작이
사라져 있었지만, 여기는 **방어가 두 겹인 것**뿐이라 동작이 틀리지 않습니다. 테스트가 "내부에서
이미 도메인 예외로 나온다"를 단언해 사실을 드러내 두었고, 지우는 것은 별도 판단으로 남깁니다.

## 7. 하지 않기로 한 것

- **커버리지 수치를 목표로 삼지 않습니다.** 11번과 같습니다 — 덮어야 할 것은 줄이 아니라 분기이고,
  특히 "실패했을 때 무엇이 일어나는가"입니다.
- **통합 테스트 인프라를 도입하지 않습니다.** 이 넷은 전부 목으로 덮을 수 있습니다. 실제 Redis가
  필요하다는 판단은 11번 4절의 Lua 스크립트 이야기이고 이 항목과 무관합니다.
- **JWT 라이브러리를 바꾸지 않았습니다.** `Jwts.builder()`·`SignatureAlgorithm`이 deprecated API를
  쓰고 있지만, 테스트가 없었다는 것이 구현을 바꿀 이유는 아닙니다. 이제 테스트가 있으므로
  올리려면 그물이 준비된 상태입니다.
- **도달 불가 catch 둘을 지우지 않았습니다.** 6절의 판단입니다.
- **인바운드 어댑터로 규칙을 넓히지 않습니다.** 컨트롤러는 대부분 위임 한 줄이라 우선순위가
  낮다는 11번의 판단이 그대로 유효합니다.

# 04. 아웃바운드 포트 구현체 3개가 인프라에 있다

> 상태 · **완료** (커밋 전, 워킹 트리)
> 성격 · 경계 | 난이도 · 중간 | 선행 항목 · 없음
> 다음 항목 · [05번 — 웹 관심사가 인프라에 있다](05-web-concerns-in-infrastructure.md). 같은 성격이고 범위만 더 큽니다. **여기서 확정한 패키지 모양과 규칙을 그대로 물려받습니다.**
> **난이도 "중간"은 과대평가였습니다** — 4절을 보세요. 실제 이동은 파일당 한 줄이었고, 시간은 대부분 "무엇이 함께 가고 무엇이 남는가"를 가리는 데 들었습니다.

## 1. 무엇이 문제인가

아웃바운드 포트 18개 중 **15개는 구현체가 `adapter.out.*`에 있고, 3개는 `infrastructure`에**
있었습니다.

| 포트 | 착수 전 구현체 위치 | 옮긴 곳 |
| --- | --- | --- |
| `EncryptPort` | `infrastructure/crypto/AesGcmEncryptor` | [`adapter/out/crypto/`](../../src/main/java/com/serverbe/adapter/out/crypto/) |
| `TokenProvider` | `infrastructure/security/JwtTokenProvider` | [`adapter/out/security/`](../../src/main/java/com/serverbe/adapter/out/security/) |
| `TokenResolver` | `infrastructure/security/JwtTokenResolver` | [`adapter/out/security/`](../../src/main/java/com/serverbe/adapter/out/security/) |

## 2. 근거

```bash
# 포트별로 구현체가 어느 패키지에 있는지 나열한다
for p in $(find src/main/java/com/serverbe/application/port/out -name "*Port.java" \
             -o -name "TokenProvider.java" -o -name "TokenResolver.java" | sort); do
  n=$(basename "$p" .java)
  impls=$(grep -rln "implements .*\b$n\b" src/main/java --include=*.java \
          | sed 's#src/main/java/com/serverbe/##;s#/[^/]*$##' | sort -u | tr '\n' ' ')
  printf "%-28s -> %s\n" "$n" "$impls"
done
```

세 줄만 `infrastructure/...`를 가리키고 나머지 열다섯 줄은 `adapter/out/...`을 가리킵니다.

```java
// AesGcmEncryptor.java — 포트를 구현하는데 인프라에 있다
@Component
public class AesGcmEncryptor implements EncryptPort {
```

## 3. 왜 고쳐야 하는가

**방금 인바운드에서 한 일의 아웃바운드 짝입니다.**

직전 작업에서 `AiNotificationSqsListener`(SQS)·`TaskTimeoutScheduler`(시각)·
`RedisGeoWarmUpListener`(기동 이벤트)를 `infrastructure`에서 `adapter.in`으로 옮겼습니다. 근거는
**"트리거가 무엇이든 흐름을 바깥에서 시작시키면 인바운드 어댑터"** 였습니다. 그 규칙을 뒤집으면
그대로 이쪽 규칙이 됩니다 — **애플리케이션이 바깥에 무언가를 요청하는 통로면 아웃바운드 어댑터**이고,
그 통로의 정의가 곧 포트입니다.

세 클래스는 정확히 그 일을 합니다. 애플리케이션은 `EncryptPort.encrypt`를 부르고 그것이 AES-GCM인지
KMS인지 모릅니다. `TokenProvider.issue`를 부르고 그것이 JWT인지 불투명 토큰인지 모릅니다. **교체
가능성이 설계에 이미 들어 있는데 위치만 그것을 부정하고 있습니다.**

실질적인 손해도 있습니다. 지금 상태에서는 `infrastructure` 패키지를 열었을 때 **"프레임워크 배선"과
"바깥 세계와의 대화"가 섞여** 있어, 어느 것이 교체 가능한 부품이고 어느 것이 스프링을 붙드는
접착제인지 구분되지 않습니다.

## 4. 어떻게 — 한 일

**함께 딸려 가는 것과 남는 것을 가리는 것이 이 항목의 실제 작업량이었습니다.** 이동 자체는
파일당 `package` 선언 한 줄이었습니다.

### 이동이 왜 이렇게 쉬웠는가

착수 전 조사에서 나온 사실 하나가 작업 전체를 결정했습니다 — **세 클래스를 이름으로 참조하는
코드가 저장소에 한 곳도 없습니다.** 전부 포트 인터페이스로 주입받습니다.

```bash
# 이동 대상을 구체 타입으로 참조하는 곳 — 자기 자신과 테스트 외에는 결과가 없다
grep -rn "AesGcmEncryptor\|JwtTokenProvider\|JwtTokenResolver" src --include=*.java
```

**이것 자체가 3절 주장의 증거입니다.** 교체 가능성은 실제로 확보되어 있었고, 어긋나 있던 것은
위치뿐이었습니다. 만약 어딘가가 `JwtTokenResolver`를 구체 타입으로 붙들고 있었다면 이 항목은
"파일 옮기기"가 아니라 "의존 끊기"였을 것입니다.

**옮겼습니다 — 포트 구현체와 그 전용 부속**

- `AesGcmEncryptor` → `adapter/out/crypto/`
- `JwtTokenProvider`, `JwtTokenResolver` → `adapter/out/security/`
- `JwtKeyManager` → `adapter/out/security/` (아래 판단)
- `AesGcmEncryptorTest` → `src/test/.../adapter/out/crypto/`

**남겼습니다 — 포트 구현이 아니라 스프링 배선**

`SecurityConfig`(필터 체인 구성), `TokenExtractor`·`CustomAccessDeniedHandler`·
`CustomAuthenticationEntryPoint`(시큐리티 훅), `EncryptionContext`·`EncryptionContextInterceptor`
(암호화 컨텍스트 전파), `config/properties/*`, 그리고 `SecureRandom` 빈을 제공하는
`SecurityBeanConfig`.

### `JwtKeyManager` — 문서가 열어 둔 유일한 판단

착수 전에는 "포트가 아니고, 옮기면 두 어댑터 중 어느 쪽에도 속하지 않는다"는 이유로 남기는 쪽으로
기울어 있었습니다. **옮기는 것으로 결론 냈습니다.** 기준은 **"어댑터가 죽을 때 함께 죽는가"** 입니다.

`JwtKeyManager`는 `SecretKey`와 `io.jsonwebtoken.JwtParser`를 쥔 **JWT 전용 부품**이고, 사용처는
`JwtTokenProvider`·`JwtTokenResolver` 둘뿐입니다. 토큰을 불투명 토큰으로 바꾸면 두 어댑터와 함께
삭제됩니다. **교체 가능한 부품 쪽이지, 스프링을 붙드는 접착제가 아닙니다** — 그리고 그 둘을 가르는
것이 이 항목의 목적이었습니다.

"어느 쪽에도 속하지 않는다"는 걱정은 이번 배치에서 성립하지 않았습니다. 두 어댑터가 **같은
패키지**로 갔으므로 셋이 나란히 섭니다.

### 이동만으로는 남지 않는 것

새 패키지 둘에 `package-info.java`를 두었습니다. **"무엇이 여기 있는가"보다 "무엇은 여기 없는가"가
중요합니다** — `adapter/out/crypto`에는 `EncryptionContext`가 왜 따라오지 않았는지,
`adapter/out/security`에는 `SecurityConfig`와 `TokenExtractor`가 왜 남았는지를 적었습니다.
다음 사람이 같은 판단을 다시 하지 않게 하는 것이 이동의 나머지 절반입니다.

### 확인한 것

- **`AesGcmEncryptorTest`가 수정 없이 통과합니다**(패키지 선언 한 줄만 바뀜). 통과하지 않았다면
  단순 이동이 아니었다는 뜻입니다.
- **새 위반이 생기지 않았습니다.** 옮긴 클래스들이 `infrastructure.config.properties`를 계속
  import하지만, `GoogleOAuthAdapter`·`KakaoOAuthAdapter`·`S3AiInputAdapter` 등 **여섯 어댑터가
  이미 같은 일을 하고 있는 확립된 선례**입니다. 막는 규칙은 `application → infrastructure`이지
  `adapter → infrastructure`가 아닙니다.
- **스프링 배선은 손댈 것이 없었습니다.** `@SpringBootApplication`에 `scanBasePackages` 제한이 없어
  스캔 범위가 `com.serverbe..` 전체이고, 넷 다 그 안에서 움직였습니다. 다만 컨텍스트 로딩 테스트는
  MySQL·Redis가 필요한 `integrationTest` 태스크에 있어 이번에는 돌리지 못했습니다 — **남은 확인**입니다.
- `adapter.in`에서 `infrastructure.security.TokenExtractor`를 import하는 두 곳은 그대로입니다 —
  **05번의 대상**입니다.

## 5. 재발 방지

`LayerDependencyTest`에 규칙을 추가합니다. **이 규칙이 이 항목의 진짜 산출물입니다** — 클래스를
옮기는 것은 한 번이지만, 규칙은 다음번에도 막아 줍니다.

```java
static final ArchRule 아웃바운드_포트_구현체는_어댑터다 = classes()
        .that().implement(resideInAPackage("com.serverbe.application.port.out.."))
        .should().resideInAPackage("com.serverbe.adapter.out..");
```

인바운드 쪽에는 이미 `바깥이_흐름을_시작시키면_인바운드_어댑터다`가 있으므로, 이 둘이 짝을 이뤄
어댑터 경계를 **양방향으로** 고정합니다.

**조건을 이름이 아니라 패키지로 잡은 것이 핵심입니다.** `application.port.out.security`에는
`TokenProvider`·`TokenResolver`처럼 `Port`로 끝나지 않는 이름이 있습니다. `*Port`를 보는 규칙이었다면
하필 **이번 위반 셋 중 둘이 규칙을 통과**했을 것입니다. 규칙이 잡아야 할 것을 정확히 놓치는 방식이라
더 위험합니다 — [06번](06-framework-exception-leak.md)이 지금까지 살아남은 이유도 규칙이 이름만
봤기 때문입니다.

### 규칙이 정말 잡는지 확인했습니다

**항상 통과하는 규칙은 규칙이 아닙니다.** `JwtTokenProvider`를 잠깐 `infrastructure`로 되돌려 놓고
돌려 실패하는 것을 보고, 다시 옮겼습니다.

```
LayerDependencyTest > 아웃바운드_포트_구현체는_어댑터다 FAILED
7 tests completed, 1 failed
```

## 6. 하지 않기로 한 것

- **`infrastructure` 패키지를 없애지 않습니다.** 스프링 배선, 프로퍼티, AOP, 시큐리티 설정은
  어댑터도 애플리케이션도 아닙니다. 그것들이 모일 자리는 필요합니다.
- **포트 인터페이스를 손대지 않습니다.** `TokenProvider`·`TokenResolver`는 이미 포트 계층에서
  Spring Security 타입이 제거된 상태입니다(커밋 `8345984`). 이 항목은 **구현체의 위치**만
  다룹니다.
- **`EncryptPort`를 도메인으로 올리지 않습니다.** 암호화는 영속화 경계에서 일어나는 일이고,
  도메인은 평문을 다룹니다. 지금 자리가 맞습니다.

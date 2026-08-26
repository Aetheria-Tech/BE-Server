# 04. 아웃바운드 포트 구현체 3개가 인프라에 있다

> 상태 · 대기
> 성격 · 경계 | 난이도 · 중간 | 선행 항목 · 없음
> 다음 항목 · [05번 — 웹 관심사가 인프라에 있다](05-web-concerns-in-infrastructure.md). 같은 성격이고 범위만 더 큽니다. **여기를 먼저 닫습니다.**

## 1. 무엇이 문제인가

아웃바운드 포트 18개 중 **15개는 구현체가 `adapter.out.*`에 있고, 3개는 `infrastructure`에**
있습니다.

| 포트 | 지금 구현체 위치 | 가야 할 곳 |
| --- | --- | --- |
| `EncryptPort` | `infrastructure/crypto/AesGcmEncryptor` | `adapter/out/crypto/` |
| `TokenProvider` | `infrastructure/security/JwtTokenProvider` | `adapter/out/security/` |
| `TokenResolver` | `infrastructure/security/JwtTokenResolver` | `adapter/out/security/` |

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

## 4. 어떻게

**함께 딸려 가는 것과 남는 것을 먼저 가릅니다.** 이게 이 항목의 실제 작업량입니다.

**옮깁니다 — 포트 구현체와 그 전용 부속**

- `AesGcmEncryptor` → `adapter/out/crypto/`
- `JwtTokenProvider` → `adapter/out/security/`
- `JwtTokenResolver` → `adapter/out/security/`

**남깁니다 — 포트 구현이 아니라 스프링 배선**

- `SecurityConfig` — 시큐리티 필터 체인 구성. 프레임워크 배선입니다
- `JwtKeyManager` — 키 로딩·회전. `JwtTokenProvider`/`JwtTokenResolver`가 함께 쓰는 부품이지만
  포트가 아니고, 옮기면 두 어댑터 중 어느 쪽에도 속하지 않습니다. **판단이 갈릴 수 있는 유일한
  지점이므로 실제 작업 시 다시 봅니다**
- `TokenExtractor`, `CustomAccessDeniedHandler`, `CustomAuthenticationEntryPoint` — 시큐리티 훅
- `EncryptionContext`, `EncryptionContextInterceptor` — 암호화 컨텍스트 전파. 영속성 어댑터의
  `CryptoConverter`가 쓰므로 함께 볼 필요가 있습니다
- `config/properties/*` — 프로퍼티 바인딩

**확인해야 할 것**

- `adapter.in`에서 `infrastructure.security.TokenExtractor`를 import하는 곳이 두 군데 있습니다.
  옮기는 것과 무관하지만, 05번을 할 때 다시 마주칩니다
- `AesGcmEncryptorTest`가 있으므로 **패키지 이동 후에도 그대로 통과해야** 합니다. 통과하지 않으면
  단순 이동이 아니었다는 뜻입니다

## 5. 재발 방지

`LayerDependencyTest`에 규칙을 추가합니다. **이 규칙이 이 항목의 진짜 산출물입니다** — 클래스를
옮기는 것은 한 번이지만, 규칙은 다음번에도 막아 줍니다.

```
아웃바운드_포트_구현체는_어댑터다
  application.port.out.. 의 인터페이스를 구현하는 클래스는
  adapter.out.. 에 있어야 한다
```

인바운드 쪽에는 이미 `바깥이_흐름을_시작시키면_인바운드_어댑터다`가 있으므로, 이 둘이 짝을 이뤄
어댑터 경계를 양방향으로 고정하게 됩니다.

구현 시 주의 — `application.port.out.security`에는 `TokenProvider`·`TokenResolver`처럼 `Port`로
끝나지 않는 이름이 있습니다. 규칙을 **이름이 아니라 패키지**로 잡아야 이들이 빠지지 않습니다.

## 6. 하지 않기로 한 것

- **`infrastructure` 패키지를 없애지 않습니다.** 스프링 배선, 프로퍼티, AOP, 시큐리티 설정은
  어댑터도 애플리케이션도 아닙니다. 그것들이 모일 자리는 필요합니다.
- **포트 인터페이스를 손대지 않습니다.** `TokenProvider`·`TokenResolver`는 이미 포트 계층에서
  Spring Security 타입이 제거된 상태입니다(커밋 `8345984`). 이 항목은 **구현체의 위치**만
  다룹니다.
- **`EncryptPort`를 도메인으로 올리지 않습니다.** 암호화는 영속화 경계에서 일어나는 일이고,
  도메인은 평문을 다룹니다. 지금 자리가 맞습니다.

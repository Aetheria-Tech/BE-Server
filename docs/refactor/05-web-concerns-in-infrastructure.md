# 05. 웹 관심사가 인프라에 있다

> 상태 · 대기
> 성격 · 경계 | 난이도 · 높음(파급 범위) | 선행 항목 · [04번](04-outbound-adapter-location.md) — **완료됨.** 패키지 모양과 규칙이 확정됐습니다
> 04번과 같은 성격입니다. 좁은 쪽에서 확정한 것을 그대로 물려받습니다 — **포트를 구현하면 어댑터**라는 규칙이 `아웃바운드_포트_구현체는_어댑터다`로 이미 서 있고, 새 패키지에는 `package-info.java`로 "무엇이 여기 있고 무엇이 인프라에 남았는지"를 적는 형식이 정해졌습니다.

## 1. 무엇이 문제인가

HTTP 응답을 만드는 세 가지가 `infrastructure`에 있습니다. 셋은 서로 얽혀 있어 **한 덩어리로 움직여야**
합니다.

| 대상 | 지금 위치 | 무엇인가 |
| --- | --- | --- |
| `BusinessExceptionHandler` | `infrastructure/error/` | `@RestControllerAdvice`. 예외를 HTTP 응답으로 바꾸는 **인바운드 웹 어댑터** |
| `RestApiResponse`(+ 중첩 `ApiError`) | `infrastructure/common/response/` | `HttpStatus`를 필드로 갖는 HTTP 응답 봉투 |
| `ErrorKindHttpStatusMapper` | `infrastructure/error/` | 도메인 `ErrorKind` → `HttpStatus` 변환. 위 둘이 함께 씀 |

## 2. 근거

```bash
# 어댑터가 인프라의 무엇에 의존하는지 센다
grep -rh "^import com.serverbe.infrastructure" src/main/java/com/serverbe/adapter --include=*.java \
  | sed 's/import //;s/;//' | sort | uniq -c | sort -rn
```

가장 많이 나오는 것이 `RestApiResponse`이고, **컨트롤러 6개**가 이 한 타입 때문에
`adapter.in.web → infrastructure` 방향으로 의존합니다.

```java
// BusinessExceptionHandler.java — HTTP 응답을 만드는데 인프라에 있다
@RestControllerAdvice
public class BusinessExceptionHandler {
```

## 3. 왜 고쳐야 하는가

`@RestControllerAdvice`는 **컨트롤러의 일부**입니다. 스프링 MVC가 컨트롤러에서 던져진 예외를
가로채 응답 본문과 상태 코드를 만드는 자리이고, 컨트롤러가 정상 경로에서 하는 일과 정확히 같은
일을 예외 경로에서 합니다. 컨트롤러가 `adapter.in.web`에 있다면 이것도 거기 있어야 합니다.

`RestApiResponse`는 더 분명합니다. `HttpStatus`를 필드로 들고 `@JsonInclude`로 직렬화 모양을
정합니다 — **HTTP와 JSON 둘 다에 묶여 있습니다.** 이보다 더 웹 어댑터스러운 타입은 없습니다.

지금 배치가 만드는 실제 결과는 이렇습니다. **웹 프로토콜을 바꾸는 상상을 하면 `infrastructure`를
열어야 합니다.** 인바운드 어댑터를 `adapter.in`에 모아 둔 이유가 "진입점은 한곳에서 보인다"였는데,
진입점의 응답 규격만 다른 데 있습니다.

04번과 이 항목은 같은 문장으로 요약됩니다 — **자리를 정하는 것은 기술이 아니라 방향입니다.**

## 4. 어떻게

**세 개를 한 번에 옮깁니다.** 나눠서 옮기면 `RestApiResponse`가 `ErrorKindHttpStatusMapper`를
참조하는 동안 `adapter → infrastructure` 의존이 그대로 남아, 중간 상태가 지금보다 나을 게 없습니다.

```
adapter/in/web/response/   ← RestApiResponse, ApiError
adapter/in/web/error/      ← BusinessExceptionHandler, ErrorKindHttpStatusMapper
```

**함께 움직이는 것**

- 컨트롤러 6개의 import 한 줄씩
- `AiTestController`도 `RestApiResponse`를 씁니다(`@Profile({"local","dev"})`)
- 테스트 두 개 — `RestApiResponseJsonContractTest`, `ErrorKindHttpStatusMapperTest`

**반드시 지켜야 할 것**

`RestApiResponseJsonContractTest`가 **JSON 모양을 고정하고 있습니다.** 이 테스트가 존재하는
이유는 클라이언트가 이 봉투의 필드명에 의존하기 때문입니다. 패키지가 바뀌어도
**필드명과 직렬화 결과는 한 글자도 달라져서는 안 됩니다.** 이 테스트가 그대로 통과하는 것이
"단순 이동이었다"는 증거입니다.

**남기는 것**

- `infrastructure/common/logging/*` — `Trace`·`Timer` AOP. 웹이 아니라 횡단 관심사입니다
- `infrastructure/util/ClientIpUtils`, `DeviceUtils` — 어댑터가 쓰지만 HTTP 헤더 파싱 유틸이라
  판단이 갈립니다. **이 항목에서는 건드리지 않고**, 옮긴 뒤에도 `adapter → infrastructure` 의존이
  남는지 보고 별도로 판단합니다

## 5. 재발 방지

04번에서 추가하는 `아웃바운드_포트_구현체는_어댑터다`로는 이걸 잡을 수 없습니다. 이 셋은 포트를
구현하지 않기 때문입니다. 그래서 별도 규칙을 둡니다.

```
웹_애노테이션이_붙은_클래스는_웹_어댑터다
  @RestController / @RestControllerAdvice / @ControllerAdvice 가 붙은 클래스는
  adapter.in.web.. 에 있어야 한다
```

인바운드 규칙(`바깥이_흐름을_시작시키면_인바운드_어댑터다`)이 메서드 애노테이션을 봤다면 이쪽은
**클래스 애노테이션**이므로 ArchUnit 기본 술어로 바로 표현됩니다.

`RestApiResponse` 같은 **타입**은 애노테이션이 없어 규칙으로 잡히지 않습니다. 그건 04번 규칙과
이 규칙이 함께 좁혀 준 뒤 남는 잔여물이고, 다음 문장으로 대신합니다 — **`HttpStatus`를 필드로 갖는
타입은 웹 어댑터에 있어야 합니다.**

## 6. 하지 않기로 한 것

- **응답 봉투의 모양을 바꾸지 않습니다.** `success`·`httpStatus`·`data`·`error` 구조에 개선할
  여지가 있더라도(예: `httpStatus`를 본문에 싣는 것이 중복인가) 이 항목은 **이사만** 합니다.
  모양을 바꾸는 것은 클라이언트 호환성 판단이 필요한 별개의 일입니다.
- **예외 핸들러를 쪼개지 않습니다.** 213줄이지만 `@ExceptionHandler` 메서드가 나란히 있는 형태라
  길이가 곧 복잡도는 아닙니다. 옮기고 나서도 여전히 문제로 보이면 그때 새 항목으로 엽니다.
- **`ErrorKind` → `HttpStatus` 매핑을 도메인으로 되돌리지 않습니다.** 도메인에서 `HttpStatus`를
  걷어낸 것이 커밋 `0254366`의 결론이고, 그 판단은 유효합니다.

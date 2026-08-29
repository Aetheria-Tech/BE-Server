# 05. 웹 관심사가 인프라에 있다

> 상태 · **완료**
> 성격 · 경계 | 난이도 · 높음(파급 범위) | 선행 항목 · [04번](04-outbound-adapter-location.md) — 완료됨
> 다음 항목 · [06번 — 프레임워크 예외 누수](06-framework-exception-leak.md)
> **"난이도 높음(파급 범위)"도 과대평가였습니다** — 04번과 같은 이유입니다. 파급은 넓었지만
> 파일당 한 줄이었고, 시간은 **문서가 예측하지 못한 것 두 가지**를 처리하는 데 들었습니다(4절).

## 1. 무엇이 문제였는가

HTTP 응답을 만드는 세 가지가 `infrastructure`에 있었습니다. 셋은 서로 얽혀 있어 **한 덩어리로
움직여야** 했습니다.

| 대상 | 착수 전 위치 | 옮긴 곳 |
| --- | --- | --- |
| `BusinessExceptionHandler` | `infrastructure/error/` | [`adapter/in/web/error/`](../../src/main/java/com/serverbe/adapter/in/web/error/) |
| `ErrorKindHttpStatusMapper` | `infrastructure/error/` | [`adapter/in/web/error/`](../../src/main/java/com/serverbe/adapter/in/web/error/) |
| `RestApiResponse`(+ 중첩 `ApiError`) | `infrastructure/common/response/` | [`adapter/in/web/response/`](../../src/main/java/com/serverbe/adapter/in/web/response/) |

## 2. 근거

```bash
# 어댑터가 인프라의 무엇에 의존하는지 센다
grep -rh "^import com.serverbe.infrastructure" src/main/java/com/serverbe/adapter --include=*.java \
  | sed 's/import //;s/;//' | sort | uniq -c | sort -rn
```

가장 많이 나오는 것이 `RestApiResponse`였고, **컨트롤러 6개**가 이 한 타입 때문에
`adapter.in.web → infrastructure` 방향으로 의존했습니다.

```
착수 전                                                        착수 후
6 infrastructure.common.response.RestApiResponse   ← 1위        (사라짐)
5 infrastructure.config.properties.JwtProperties               5 ...JwtProperties
2 infrastructure.util.DeviceUtils                              2 ...DeviceUtils
2 infrastructure.security.TokenExtractor                       2 ...TokenExtractor
...                                                            ...
```

```java
// BusinessExceptionHandler.java — HTTP 응답을 만드는데 인프라에 있었다
@RestControllerAdvice
public class BusinessExceptionHandler {
```

## 3. 왜 고쳤는가

`@RestControllerAdvice`는 **컨트롤러의 일부**입니다. 스프링 MVC가 컨트롤러에서 던져진 예외를
가로채 응답 본문과 상태 코드를 만드는 자리이고, 컨트롤러가 정상 경로에서 하는 일과 정확히 같은
일을 예외 경로에서 합니다. 컨트롤러가 `adapter.in.web`에 있다면 이것도 거기 있어야 합니다.

`RestApiResponse`는 더 분명합니다. `HttpStatus`를 필드로 들고 `@JsonInclude`로 직렬화 모양을
정합니다 — **HTTP와 JSON 둘 다에 묶여 있습니다.** 이보다 더 웹 어댑터스러운 타입은 없습니다.

착수 전 배치가 만든 실제 결과는 이랬습니다. **웹 프로토콜을 바꾸는 상상을 하면 `infrastructure`를
열어야 했습니다.** 인바운드 어댑터를 `adapter.in`에 모아 둔 이유가 "진입점은 한곳에서 보인다"였는데,
진입점의 응답 규격만 다른 데 있었습니다.

04번과 이 항목은 같은 문장으로 요약됩니다 — **자리를 정하는 것은 기술이 아니라 방향입니다.**

## 4. 어떻게 — 한 일

**세 개를 한 번에 옮겼습니다.** 나눠서 옮기면 `RestApiResponse`가 `ErrorKindHttpStatusMapper`를
참조하는 동안 `adapter → infrastructure` 의존이 그대로 남아, 중간 상태가 착수 전보다 나을 게
없었습니다.

```
adapter/in/web/response/   ← RestApiResponse (+ ApiError)
adapter/in/web/error/      ← BusinessExceptionHandler, ErrorKindHttpStatusMapper
```

**함께 움직인 것 — 파일당 한 줄**

- 컨트롤러 6개의 import 한 줄씩 (`AiTestController` 포함 — `@Profile({"local","dev"})`)
- 테스트 두 개가 **반드시** 함께 갔습니다. 둘 다 `import` 없이 **같은 패키지 가시성**에
  기대고 있어(테스트 파일에 대상 타입 import가 한 줄도 없습니다) 본체만 옮기면 컴파일이 깨집니다
- `domain/exception/ErrorKind.java`의 Javadoc 문자열 한 줄

### 문서가 예측하지 못한 것 두 가지

**이것이 이 항목의 실제 작업량이었습니다.** 착수 전 문서는 "함께 움직이는 것"을 컨트롤러 6개와
테스트 2개로 봤는데, 둘 다 틀렸습니다.

**하나 — 테스트는 셋이었습니다.** `RunningArtPageJsonContractTest`(`adapter/in/web/`)도
`RestApiResponse`를 import합니다. 이쪽은 애초에 어댑터에 있어 이동 대상이 아니었고 import 한 줄만
바뀌었지만, **"둘"이라고 적힌 문서를 그대로 믿었다면 컴파일 에러로 발견**했을 것입니다.

**둘 — 인프라가 응답 봉투를 씁니다.** `infrastructure/security/CustomAuthenticationEntryPoint`와
`CustomAccessDeniedHandler`가 `RestApiResponse.fail(...)`을 씁니다. 문서는 이 둘을 한 번도
언급하지 않았습니다.

**둘을 인프라에 남기기로 했습니다.** 04번이 "포트 구현이 아니라 프레임워크 배선"이라며 의도적으로
남긴 클래스들이고, 스프링 MVC **밖**(필터 체인)에서 `HttpServletResponse`에 직접 JSON을 쓰기 때문에
`BusinessExceptionHandler`가 잡을 수 없습니다. 05번의 범위는 셋을 옮기는 것이지 04번의 판단을
뒤집는 것이 아닙니다.

대신 이 결정으로 **`infrastructure → adapter.in.web`** — 인프라가 어댑터를 아는 방향이
새로 생겼습니다. **막는 ArchUnit 규칙이 없어 조용히 통과합니다.** 그래서
[`adapter/in/web/response/package-info.java`](../../src/main/java/com/serverbe/adapter/in/web/response/package-info.java)에
사실과 이유를 명시적으로 적었습니다. 다음 사람이 이걸 발견하고 "실수인가?" 하고 되짚지 않게
하는 것이 목적입니다. 두 훅의 자리는 별도로 판단할 대상입니다.

### 이동만으로는 남지 않는 것

04번과 같이 새 패키지 둘에 `package-info.java`를 두었습니다. **"무엇이 여기 있는가"보다 "무엇은
여기 없는가"가 중요합니다** — `error`에는 시큐리티 훅 둘이 왜 따라오지 않았는지, `response`에는
위의 역방향 의존과 **필드명이 클라이언트 계약이라는 것**을 적었습니다.

인프라 쪽 `package-info.java` 둘도 정리했습니다. 둘 다 **이미 낡아 있었습니다** —
`infrastructure/error/`는 존재하지 않는 `GlobalExceptionHandler`·`ErrorResponse`를 설명하고
있었고(패키지가 통째로 사라져 삭제), `infrastructure/common/`은 존재하지 않는 `BaseTimeEntity`·
`DateUtils`를 나열하고 있었습니다(횡단 관심사 패키지로 다시 씀).

### 확인한 것

- **`RestApiResponseJsonContractTest`가 수정 없이 통과합니다.** 이 테스트가 존재하는 이유는
  클라이언트가 봉투의 필드명에 의존하기 때문이고, 기대 JSON을 문자열로 하드코딩해
  `httpStatus`가 숫자가 아니라 **enum 이름**으로 나가는 것까지 고정합니다.
  **diff가 `package` 한 줄뿐인 채로 통과하는 것이 "단순 이동이었다"는 증거입니다.**

  ```
  -package com.serverbe.infrastructure.common.response;
  +package com.serverbe.adapter.in.web.response;
  ```

  `ErrorKindHttpStatusMapperTest`도 같습니다.
- **`gradlew build` 통과.** 스프링 배선은 04번과 같은 이유로 손댈 것이 없었습니다 —
  `@SpringBootApplication`에 `scanBasePackages` 제한이 없어 스캔 범위가 `com.serverbe..`
  전체입니다.
- **컨텍스트 로딩 테스트는 이번에도 못 돌렸습니다.** MySQL·Redis가 필요한 `integrationTest`
  태스크에 있습니다 — **남은 확인**입니다(04번과 동일).
- **`adapter → infrastructure`로 남은 것은 셋뿐입니다** — `config.properties.*`(04번이 확인한
  확립된 선례), `util.ClientIpUtils`·`DeviceUtils`, `security.TokenExtractor`.

### 하지 않고 남긴 것

- `infrastructure/common/logging/*` — `Trace`·`Timer` AOP. 웹이 아니라 횡단 관심사입니다.
  셋 중 무엇도 참조하지 않아 영향이 없었습니다
- `infrastructure/util/ClientIpUtils`, `DeviceUtils` — 어댑터가 쓰지만 HTTP 헤더 파싱 유틸이라
  판단이 갈립니다. 특히 `ClientIpUtils`는 **`infrastructure/config/aop/RateLimitAspect`도 씁니다.**
  그냥 옮기면 이번에 시큐리티 훅에서 본 것과 **정확히 거울상인 문제**가 생깁니다. 별도 판단입니다
- **패키지 순환.** `response → error`(`RestApiResponse.fail`)와 `error → response`
  (`BusinessExceptionHandler`)가 서로를 참조합니다. 착수 전
  `infrastructure.common.response ↔ infrastructure.error`도 똑같았으므로 **이동으로 나빠지지
  않았습니다.** 모양을 바꾸지 않는다는 원칙대로 뒀습니다

## 5. 재발 방지

04번의 `아웃바운드_포트_구현체는_어댑터다`로는 이걸 잡을 수 없습니다. 이 셋은 포트를 구현하지
않기 때문입니다. 그래서 `LayerDependencyTest`에 별도 규칙을 뒀습니다.

```java
static final ArchRule 웹_애노테이션이_붙은_클래스는_웹_어댑터다 = classes()
        .that().areAnnotatedWith(RestController.class)
        .or().areAnnotatedWith(RestControllerAdvice.class)
        .or().areAnnotatedWith(ControllerAdvice.class)
        .should().resideInAPackage("com.serverbe.adapter.in.web..");
```

인바운드 규칙(`바깥이_흐름을_시작시키면_인바운드_어댑터다`)이 **메서드** 애노테이션을 봐야 해서
커스텀 술어가 필요했다면, 이쪽은 **클래스 애노테이션**이므로 ArchUnit 기본 술어로 바로
표현됩니다.

세 애노테이션을 모두 나열한 것은 `@AnalyzeClasses`가 `DoNotIncludeJars`라
`@RestControllerAdvice`가 `@ControllerAdvice`의 메타 애노테이션이라는 사실에 기댈 수 없기
때문입니다.

### 규칙이 정말 잡는지 확인했습니다

**항상 통과하는 규칙은 규칙이 아닙니다.** 04번의 절차대로 `BusinessExceptionHandler`를 잠깐
`infrastructure.error`로 되돌려 놓고 돌려 실패하는 것을 보고, 다시 옮겼습니다.

```
LayerDependencyTest > 웹_애노테이션이_붙은_클래스는_웹_어댑터다 FAILED
8 tests completed, 1 failed

Architecture Violation [Priority: MEDIUM] - Rule 'classes that are annotated with
@RestController or are annotated with @RestControllerAdvice or are annotated with
@ControllerAdvice should reside in a package 'com.serverbe.adapter.in.web..'' was violated (1 times):
Class <com.serverbe.infrastructure.error.BusinessExceptionHandler> does not reside in
a package 'com.serverbe.adapter.in.web..'
```

### 규칙이 잡지 못하는 것

`RestApiResponse` 같은 **타입**은 애노테이션이 없어 규칙으로 잡히지 않습니다. 그건 04번 규칙과
이 규칙이 함께 좁혀 준 뒤 남는 잔여물이고, 다음 문장으로 대신합니다 — **`HttpStatus`를 필드로
갖는 타입은 웹 어댑터에 있어야 합니다.**
([`response/package-info.java`](../../src/main/java/com/serverbe/adapter/in/web/response/package-info.java)에
기록했습니다.)

`infrastructure → adapter` 방향도 잡지 못합니다(4절). 이건 규칙으로 만들 수 있지만, 만드는
순간 시큐리티 훅 둘이 걸립니다. **먼저 그 둘의 자리를 정해야 규칙을 세울 수 있습니다** —
규칙이 먼저가 아니라 판단이 먼저인 경우입니다.

## 6. 하지 않기로 한 것

- **응답 봉투의 모양을 바꾸지 않았습니다.** `success`·`httpStatus`·`data`·`error` 구조에 개선할
  여지가 있더라도(예: `httpStatus`를 본문에 싣는 것이 중복인가) 이 항목은 **이사만** 했습니다.
  모양을 바꾸는 것은 클라이언트 호환성 판단이 필요한 별개의 일입니다.
- **예외 핸들러를 쪼개지 않았습니다.** 213줄이지만 `@ExceptionHandler` 메서드가 나란히 있는
  형태라 길이가 곧 복잡도는 아닙니다. 옮기고 나서도 여전히 문제로 보이면 그때 새 항목으로 엽니다.
- **`ErrorKind` → `HttpStatus` 매핑을 도메인으로 되돌리지 않았습니다.** 도메인에서 `HttpStatus`를
  걷어낸 것이 앞선 작업의 결론이고, 그 판단은 유효합니다.
- **시큐리티 훅 둘과 `BusinessExceptionHandler`의 중복을 통합하지 않았습니다.** 핸들러에 이미
  `AuthenticationException`·`AccessDeniedException` 처리가 있어 하는 일이 겹치지만, 필터 체인에서
  터지는 예외는 MVC 핸들러가 도달하지 못해 **둘 다 필요한 구조**입니다. 통합 여부는 별개의 판단입니다.

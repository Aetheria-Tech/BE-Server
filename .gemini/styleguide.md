# Spring Boot Hybrid Architecture Guide

이 가이드는 **Spring MVC(Servlet)** 기반 위에 **Project Reactor(Mono/Flux)**를 부분적으로 도입한 하이브리드 아키텍처를 위한 코드 리뷰 및 작성 원칙을 정의한다.

## 1. 핵심 철학 (Core Philosophy)

- **실용주의(Pragmatism)**: 비동기(Mono)는 목적이 아니라 수단이다. 효율성이 필요한 곳에는 비동기를, 복잡성을 낮춰야 하는 곳에는 동기 방식을 선택한다.
- **스레드 효율성**: 외부 I/O 대기 시간이 긴 작업은 `Mono`를 통해 서블릿 스레드를 점유하지 않도록 한다.
- **안정성**: 보안(SecurityContext), 트랜잭션 관리가 복잡해지는 지점에서는 전통적인 동기 방식(MVC)을 우선한다.

## 2. 반환 타입 선택 기준 (Synchronous vs. Asynchronous)

### A. 일반 동기 방식 (Object/ResponseEntity) 추천 상황

- **보안 제어 로직**: 로그아웃(Logout), 세션 무효화 등 SecurityContext와 직접적으로 상호작용하며 즉각적인 상태 파괴가 필요한 경우.
- **단순 CRUD**: 비즈니스 로직이 단순하고 DB 응답 속도가 충분히 빠른 경우.
- **복잡한 트랜잭션**: 여러 단계의 DB 쓰기 작업이 얽혀 있어 비동기 흐름에서 트랜잭션 전파를 추적하기 어려운 경우.

### B. 비동기 방식 (Mono/Flux) 추천 상황

- **외부 API 호출**: `WebClient`를 사용하여 타사 서비스(카카오, 구글 로그인 등)와 통신할 때.
- **고부하 조회 작업**: 대량의 데이터를 가공하거나 여러 소스에서 데이터를 합쳐야 하는 경우.
- **병렬 처리**: 서로 연관 없는 여러 작업을 동시에 실행하여 전체 응답 시간을 줄여야 할 때.

## 3. 구현 규칙 (Implementation Rules)

### 컨트롤러 (Controller)

- 한 컨트롤러 내에서 동기 메서드와 비동기 메서드를 혼용하는 것을 허용한다.
- `Mono`를 반환할 때는 반드시 `subscribeOn(Schedulers.boundedElastic())`을 사용하여 블로킹 작업(JPA, JDBC 등)이 서블릿 스레드를 차단하지 않도록 격리한다.
- 로그아웃과 같이 비동기 재디스패치(Async Dispatch) 시 인증 문제가 발생할 가능성이 있는 로직은 일반 동기 방식으로 구현하는 것을 권장한다.

### 보안 (Security)

- MVC 환경이므로 `SecurityContextHolder.getContext()`를 기본으로 사용한다.
- 비동기 스레드 내에서 인증 정보가 필요할 경우, `SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)` 설정을 고려하거나 `@AuthenticationPrincipal`을 통해 파라미터로 명시적으로 전달받는다.

### 예외 처리 (Exception Handling)

- `@RestControllerAdvice`를 통해 전역 예외 처리를 수행한다.
- `Mono` 파이프라인 내부의 예외는 `Mono.error()`를 통해 전파하며, 최종적으로 Spring MVC의 ExceptionHandler가 처리하도록 한다.

### 쿠키 및 헤더 (Cookies & Headers)

- `ResponseCookie` 빌더를 사용하여 `SameSite`, `HttpOnly`, `Secure` 옵션을 명시적으로 관리한다.
- `HttpServletResponse`에 직접 헤더를 추가하는 방식과 `ResponseEntity`를 반환하는 방식 중 상황에 맞는 것을 선택하되, 비동기 흐름에서는 `ResponseEntity`를 더 권장한다.

## 4. 코드 리뷰 체크리스트 (Review Checklist)

1. **과잉 엔지니어링**: 단순한 로직인데 불필요하게 `Mono`를 사용하여 가독성을 해치지 않는가?
2. **스레드 격리**: `Mono` 내부에서 블로킹 I/O가 발생하는데 `boundedElastic` 스케줄러를 누락하지 않았는가?
3. **보안 맥락**: 비동기 전환 지점에서 `SecurityContext` 유실로 인한 401/403 에러 가능성은 없는가?
4. **일관성**: 공통 응답 규격인 `ApiResponse<T>`를 모든 메서드에서 일관되게 반환하는가?
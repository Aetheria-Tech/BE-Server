# Java & Spring Boot WebFlux 스타일 가이드

# 소개
이 문서는 Java와 Spring Boot WebFlux를 사용하는 프로젝트의 코딩 표준 및 아키텍처 원칙을 정의합니다.
리액티브 프로그래밍의 이점(고성능, 비동기 처리)을 극대화하고, 흔한 실수(Blocking Call 등)를 방지하는 것을 목표로 합니다.

# 핵심 원칙 (Core Principles)
* **Non-blocking I/O:** 메인 스레드나 이벤트 루프를 차단(Block)하는 코드는 절대 작성하지 않습니다.
* **불변성 (Immutability):** 데이터 객체는 불변으로 설계하며, 상태 변경 대신 새로운 객체를 반환하는 방식을 선호합니다.
* **선언적 스타일:** 명령형 프로그래밍(for, if 등)보다는 리액티브 연산자(`map`, `filter`, `flatMap` 등)를 사용합니다.
* **Null Safety:** WebFlux 스트림 내에서는 `null`을 절대 사용하지 않습니다. 값이 없음을 표현할 때는 `Mono.empty()`를 사용합니다.

---

# 아키텍처 및 레이어링 (Architecture)

## 1. Controller (Web Layer)
* **역할:** HTTP 요청/응답 처리 및 파라미터 검증만 담당합니다. 비즈니스 로직을 포함하지 않습니다.
* **반환 타입:** 항상 `Mono<T>` 또는 `Flux<T>`를 반환해야 합니다. `ResponseEntity`를 감쌀 때도 `Mono<ResponseEntity<T>>` 형식을 유지합니다.
* **구독 금지:** 컨트롤러에서 `.subscribe()`를 명시적으로 호출하지 않습니다. 구독은 프레임워크(WebFlux)가 처리하도록 위임합니다.

## 2. Service (Business Layer)
* **역할:** 핵심 비즈니스 로직, 트랜잭션 관리, 여러 도메인 간의 조율을 담당합니다.
* **단일 책임:** 각 서비스 메서드는 하나의 명확한 작업만 수행해야 합니다.
* **비동기 흐름 유지:** 리턴 타입은 항상 Publisher(`Mono`, `Flux`)여야 하며, 중간에 `block()`을 호출하여 흐름을 끊지 않습니다.

## 3. Repository (Data Layer)
* **Reactive Repository:** R2DBC 또는 Reactive Mongo와 같은 리액티브 드라이버를 사용해야 합니다.
* **Blocking 호출 금지:** JDBC와 같은 블로킹 드라이버나 메서드를 사용해야 할 경우, 반드시 `Schedulers.boundedElastic()`을 사용하여 별도 스레드로 격리해야 합니다.

---

# 코딩 규칙 (Coding Conventions)

## 1. 명명 규칙 (Naming)
* **클래스/인터페이스:** PascalCase (예: `UserHandler`, `PaymentService`)
* **메서드/변수:** camelCase (예: `findUserById`, `processPayment`)
* **테스트:** `대상_상황_기대결과` 형식을 권장합니다. (예: `createUser_WithValidData_ReturnsMonoUser`)

## 2. 리액티브 연산자 사용 (Operators)
* **Map vs FlatMap:**
    * 동기적인 변환(단순 값 변경)에는 `map`을 사용합니다.
    * 비동기 호출(DB 조회, 외부 API 호출)이 포함된 변환에는 `flatMap`을 사용합니다.
* **Nesting 방지:** 콜백 지옥처럼 중첩된 `flatMap`은 피하고, 메서드 체이닝(Chaining)이나 `zip`, `zipWith`를 활용하여 가독성을 높입니다.

## 3. 블로킹 호출 금지 (NO Blocking)
* **엄격 금지:** `.block()`, `.blockFirst()`, `.blockLast()`는 테스트 코드를 제외하고는 절대 사용하지 않습니다.
* **Thread Sleep 금지:** `Thread.sleep()` 대신 `Duration`과 함께 `delayElement` 등을 사용합니다.

---

# 에러 처리 (Error Handling)

* **Try-Catch 지양:** 리액티브 체인 내부에서 전통적인 `try-catch` 블록 사용을 피합니다.
* **리액티브 에러 연산자 사용:**
    * 에러 발생 시 대체 값을 반환하려면: `onErrorResume` 또는 `onErrorReturn`
    * 에러를 다른 에러로 변환하려면: `onErrorMap`
* **Global Exception Handling:** 비즈니스 예외는 `@RestControllerAdvice`와 `ExceptionHandler`를 통해 전역적으로 처리하여 일관된 에러 응답 포맷을 유지합니다.

---

# 로깅 (Logging)

* **위치:** 로깅은 사이드 이펙트(Side-effect)이므로 `doOnNext`, `doOnError`, `doOnSubscribe` 등의 연산자 내부에서 수행합니다.
* **적절성:**
    * `DEBUG`: 개발 단계의 상세 흐름.
    * `INFO`: 주요 비즈니스 이벤트 성공 (예: 결제 완료).
    * `ERROR`: 예외 발생 및 스택트레이스. (`doOnError` 활용)
* **Slf4j 사용:** `System.out.println` 대신 Slf4j(`@Slf4j`)를 사용합니다.

---

# 예시 코드 (Example)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    // 좋은 예: Non-blocking, 메서드 체이닝, 명확한 에러 처리
    public Mono<UserDto> updateUser(String id, UserUpdateDto updateDto) {
        return userRepository.findById(id)
            .switchIfEmpty(Mono.error(new UserNotFoundException(id))) // 데이터 없음 처리
            .flatMap(user -> {
                user.updateInfo(updateDto.getName(), updateDto.getEmail());
                return userRepository.save(user); // DB 저장 (비동기)
            })
            .map(UserMapper::toDto) // 동기 변환은 map 사용
            .doOnSuccess(dto -> log.info("User updated successfully: {}", dto.getId())) // 로깅
            .doOnError(ex -> log.error("Failed to update user: {}", id, ex)); // 에러 로깅
    }

    // 나쁜 예: block() 사용, try-catch 혼용
    /*
    public UserDto updateUserBad(String id, UserUpdateDto updateDto) {
        try {
            User user = userRepository.findById(id).block(); // 절대 금지!
            if (user == null) throw new RuntimeException("User not found");
            
            user.setName(updateDto.getName());
            userRepository.save(user).block(); // 절대 금지!
            return UserMapper.toDto(user);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }
    */
}
```
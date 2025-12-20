# 프로젝트 스타일 가이드: Semi-Reactive Hybrid Architecture

이 문서는 Gemini Code Assist가 본 프로젝트의 코드를 생성하거나 리뷰할 때 준수해야 할 원칙을 정의합니다. 본 프로젝트는 **Semi-Reactive(Hybrid)** 모델을 채택하고 있습니다.

## 1. 핵심 아키텍처 원칙 (Semi-Reactive)

### 1.1 하이브리드 스레드 모델 및 반환 타입

- **엔드투엔드 비동기 체인**: 모든 Controller, Service, Port는 원칙적으로 `Mono<T>` 또는 `Flux<T>`를 반환하여 Netty 이벤트 루프의 효율성을 극대화합니다.
- **외부 I/O (WebClient)**: 논블로킹(Non-blocking) 방식으로 처리하며 이벤트를 기다리는 동안 쓰레드를 점유하지 않습니다.
- **내부 로직 & DB (JPA/Redis)**: JDBC 기반의 JPA와 같은 동기식(Blocking) 라이브러리를 사용합니다.
- **스레드 전환 (CRITICAL)**: 비동기 체인 내부에서 JPA 등 동기식 라이브러리를 호출할 경우, **반드시** `publishOn(Schedulers.boundedElastic())` 또는 `subscribeOn(Schedulers.boundedElastic())`을 사용하여 작업 스레드를 이벤트 루프에서 블로킹 전용 스레드 풀로 전환해야 합니다.

### 1.2 .block() 사용 금지

- 어떠한 계층에서도 `.block()` 또는 `.blockFirst()`를 호출하여 스레드를 강제로 대기시키지 않습니다.
- 모든 흐름은 비동기 파이프라인으로 연결되어 최종적으로 프레임워크가 처리하도록 합니다.

## 2. 코드 구현 가이드라인

### 2.1 WebClient 사용 (외부 연동)

- 외부 API 호출 시 `WebClient`를 사용하며, 결과는 `Mono<T>` 등으로 반환합니다.
- 비즈니스 에러 발생 시 `Mono.error(new BusinessException(...))`를 반환하여 체인 내에서 예외가 흐르도록 합니다.

### 2.2 동기 라이브러리 연동 패턴 (MANDATORY)

비동기 흐름 중에 JPA와 같은 블로킹 작업이 필요할 경우 아래 패턴을 엄격히 준수합니다.

```
public Mono<Entity> someServiceMethod(Long id) {
    return adapter.asyncCall(id) // 1. 외부 API 호출 (비동기)
        .publishOn(Schedulers.boundedElastic()) // 2. 블로킹 작업을 위한 스레드 전환
        .map(result -> {
            // 3. 여기서부터 JPA 등 블로킹 작업 수행 (안전함)
            return repository.save(new Entity(result));
        }); // 4. 결과를 Mono로 유지하여 반환
}

```

### 2.3 데이터베이스 계층 (JPA)

- `UserRepositoryPort` 등 레포지토리 포트는 표준 JPA 인터페이스를 사용합니다.
- 이벤트 루프 스레드(`reactor-http-nio-*`)에서 직접 JPA 메서드를 호출하는 것은 시스템 전체의 성능 마비를 초래하므로 절대 금지합니다.

## 3. 도메인 및 예외 처리

### 3.1 에러 핸들링

- `BusinessException`과 `ErrorMessage`를 사용하여 예외를 관리합니다.
- 비동기 체인 내부의 예외는 `onErrorResume` 또는 `switchIfEmpty` 등을 사용하여 우아하게 처리합니다.

### 3.2 JWT 및 보안

- 만료된 토큰에서도 정보를 추출해야 하는 reissue 로직의 경우, `ExpiredJwtException`에서 `Claims`를 추출하는 방식을 사용합니다.
- 일반 인가 로직에서는 토큰 만료 시 즉시 에러를 발생시킵니다.

## 4. Gemini Code Assist를 위한 지시사항 (Prompt Context)

- **리뷰 시**: 코드가 WebFlux의 이벤트 루프 스레드에서 블로킹 작업(JPA, Thread.sleep 등)을 수행하고 있는지 최우선으로 검토하십시오.
- **생성 시**: 헥사고날 아키텍처를 준수하며, 모든 API의 끝점까지 `Mono/Flux`가 유지되도록 코드를 작성하십시오.
- **수정 시**: `.block()`이 포함된 기존 코드를 발견하면, 이를 제거하고 `publishOn`과 `flatMap/map`을 이용한 비동기 체인으로 변환할 것을 제안하십시오.
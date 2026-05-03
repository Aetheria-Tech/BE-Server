package com.serverbe.application.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD) // 메서드에만 붙일 수 있음
@Retention(RetentionPolicy.RUNTIME) // 런타임까지 유지
@Repeatable(RateLimits.class)
public @interface RateLimit {
    
    // 이 API 전용 커스텀 용량 (기본값 설정 가능)
    int capacity() default 5; 
    
    // 이 API 전용 커스텀 리필 속도
    int refillRate() default 5;

    // 클라이언트에게 알려줄 재시도 대기 시간 (초 단위)
    int retryAfterSeconds() default 60;
    
    // 타겟: "IP" 기준인지, "USER" 기준인지
    TargetType target() default TargetType.IP;

    enum TargetType {
        IP, USER
    }
}
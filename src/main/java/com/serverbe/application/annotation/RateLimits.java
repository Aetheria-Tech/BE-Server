package com.serverbe.application.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimits {
    RateLimit[] value(); // 여러 개의 @RateLimit을 배열로 담는 역할
}
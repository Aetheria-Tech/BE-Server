package com.serverbe.infrastructure.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class TraceAspect {

    private final int tagWidth = 11;      // [EXCEPTION] 길이에 맞춤
    private final int locationWidth = 45; // 클래스.메서드명 너비

    @Around("@within(com.serverbe.infrastructure.common.logging.Trace)")
    public Object logTrace(ProceedingJoinPoint joinPoint) throws Throwable {
        String location = joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName();

        log.info("{}| Args: {}", formatLogPrefix("[ENTRY]", location), Arrays.toString(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            log.info("{}| Return: {}", formatLogPrefix("[EXIT]", location), result);
            return result;
        } catch (Throwable e) {
            log.error("{}| Exception: {}", formatLogPrefix("[EXCEPTION]", location), e.getClass().getSimpleName());
            throw e;
        }
    }

    private String formatLogPrefix(String tag, String location) {
        String formattedTag = String.format("%-" + tagWidth + "s", tag);
        String formattedLocation = String.format("%-" + locationWidth + "s", location);
        return formattedTag + " " + formattedLocation;
    }
}
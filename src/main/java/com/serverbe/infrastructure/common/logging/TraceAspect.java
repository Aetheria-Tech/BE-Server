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

    // ANSI 컬러 코드 상수 정의
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_CYAN = "\u001B[36m";

    private static final int TAG_WIDTH = 11;      // [EXCEPTION] 길이에 맞춤
    private static final int LOCATION_WIDTH = 45; // 클래스.메서드명 너비

    @Around("@within(com.serverbe.infrastructure.common.logging.Trace)")
    public Object logTrace(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String location = className + "." + methodName;

        String entryTag = String.format("%-" + TAG_WIDTH + "s", "[ENTRY]");
        String entryLocation = String.format("%-" + LOCATION_WIDTH + "s", location);
        log.info("{}{} {}| Args: {}", ANSI_GREEN, entryTag, entryLocation, Arrays.toString(joinPoint.getArgs()) + ANSI_RESET);

        try {
            Object result = joinPoint.proceed();

            // [EXIT] 정렬 및 출력
            String exitTag = String.format("%-" + TAG_WIDTH + "s", "[EXIT]");
            String exitLocation = String.format("%-" + LOCATION_WIDTH + "s", location);
            log.info("{}{} {}| Return: {}", ANSI_BLUE, exitTag, exitLocation, result + ANSI_RESET);

            return result;
        } catch (Throwable e) {
            // [EXCEPTION] 정렬 및 출력
            String exTag = String.format("%-" + TAG_WIDTH + "s", "[EXCEPTION]");
            String exLocation = String.format("%-" + LOCATION_WIDTH + "s", location);
            log.error("{}{} {}| Exception: {}", ANSI_RED, exTag, exLocation, e.getClass().getSimpleName() + ANSI_RESET);

            throw e;
        }
    }
}
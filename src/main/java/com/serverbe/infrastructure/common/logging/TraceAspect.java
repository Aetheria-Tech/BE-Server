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

    // 정렬을 위한 너비 설정 (이것은 스타일이 아니라 데이터 포맷이므로 Java에 두어도 무방합니다)
    private static final int TAG_WIDTH = 11;      // [EXCEPTION] 길이에 맞춤
    private static final int LOCATION_WIDTH = 45; // 클래스.메서드명 너비

    @Around("@within(com.serverbe.infrastructure.common.logging.Trace)")
    public Object logTrace(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String location = className + "." + methodName;

        // [ENTRY] 출력: 색상 코드 제거, 순수 텍스트 정렬만 수행
        String entryTag = String.format("%-" + TAG_WIDTH + "s", "[ENTRY]");
        String entryLocation = String.format("%-" + LOCATION_WIDTH + "s", location);
        log.info("{} {}| Args: {}", entryTag, entryLocation, Arrays.toString(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();

            // [EXIT] 출력
            String exitTag = String.format("%-" + TAG_WIDTH + "s", "[EXIT]");
            String exitLocation = String.format("%-" + LOCATION_WIDTH + "s", location);
            log.info("{} {}| Return: {}", exitTag, exitLocation, result);

            return result;
        } catch (Throwable e) {
            // [EXCEPTION] 출력
            String exTag = String.format("%-" + TAG_WIDTH + "s", "[EXCEPTION]");
            String exLocation = String.format("%-" + LOCATION_WIDTH + "s", location);
            log.error("{} {}| Exception: {}", exTag, exLocation, e.getClass().getSimpleName());

            throw e;
        }
    }
}
package com.serverbe.infrastructure.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Aspect
@Component
public class TimerAspect {

    @Around("@annotation(com.serverbe.infrastructure.common.logging.Timer)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();

            String methodName = joinPoint.getSignature().getName();
            String className = joinPoint.getSignature().getDeclaringType().getSimpleName();

            log.info("[TIMER] {}.{} | Duration: {}ms", className, methodName, stopWatch.getTotalTimeMillis());
        }
    }
}
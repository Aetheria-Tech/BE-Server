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
    @Around("@within(com.serverbe.infrastructure.common.logging.Trace)")
    public Object logTrace(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        log.info("[ENTRY] {}.{} | Args: {}", className, methodName, Arrays.toString(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            log.info("[EXIT] {}.{} | Return: {}", className, methodName, result);
            return result;
        } catch (Throwable e) {
            log.error("[EXCEPTION] {}.{} | Exception: {}", className, methodName, e.getClass().getSimpleName());
            throw e;
        }
    }
}
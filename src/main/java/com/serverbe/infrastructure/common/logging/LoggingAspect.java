package com.serverbe.infrastructure.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class LoggingAspect {
    @Before("@within(com.serverbe.infrastructure.common.logging.MethodLogging)")
    public void logMethodEntry(JoinPoint joinPoint) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("[ENTRY] {}.{} | Args: {}", className, methodName, Arrays.toString(args));
    }

    @AfterReturning(
            pointcut = "@within(com.serverbe.infrastructure.common.logging.MethodLogging)",
            returning = "result"
    )
    public void logMethodExit(JoinPoint joinPoint, Object result) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        log.info("[EXIT] {}.{} | Return: {}", className, methodName, result);
    }
}
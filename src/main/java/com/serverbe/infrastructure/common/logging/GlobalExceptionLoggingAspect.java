package com.serverbe.infrastructure.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class GlobalExceptionLoggingAspect {

    /**
     * 적용 범위를 설정합니다.
     * com.serverbe 하위 패키지의 모든 클래스 및 모든 메소드를 타겟으로 합니다.
     */
    @Pointcut("execution(* com.serverbe..*.*(..))")
    public void allMethods() {}

    /**
     * 예외가 발생했을 때만 동작합니다.
     */
    @AfterThrowing(pointcut = "allMethods()", throwing = "ex")
    public void logException(JoinPoint joinPoint, Throwable ex) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // [ERROR] 로그에 메소드 위치, 예외 메시지, 그리고 '당시 입력 파라미터'를 함께 남긴다.
        log.error("[GLOBAL ERROR] Location: {}.{} | Message: {} | Args: {}", className, methodName, ex.getMessage(), Arrays.toString(args));
        
        // 필요하다면 여기서 스택 트레이스도 찍는다.
        // log.error("Stack Trace: ", ex); 
    }
}
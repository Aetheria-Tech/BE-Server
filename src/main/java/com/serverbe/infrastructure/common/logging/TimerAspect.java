package com.serverbe.infrastructure.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * @responsibility <b>@Timer</b> 어노테이션이 선언된 메서드의 실행 시간을 측정하고 로그를 남기는 <b>Aspect</b>입니다.
 * @implSpec {@link StopWatch}를 사용하여 실제 로직의 실행 시간을 측정하며, AOP를 통해 비즈니스 로직과 모니터링 로직을 분리합니다.
 */
@Slf4j
@Aspect
@Component("customTimerAspect")
public class TimerAspect {

    /**
     * @responsibility 대상 메서드 실행 전후의 시간을 측정하여 클래스명, 메서드명과 함께 실행 시간을 로그로 출력합니다.
     * @implNote 로그 레벨이 <b>INFO</b> 활성 상태일 때만 측정을 수행하여 불필요한 리소스 소모를 방지합니다.
     * @param joinPoint 실행 지점의 정보를 담고 있는 {@link ProceedingJoinPoint}
     * @return 대상 메서드 실행 결과
     * @throws Throwable 메서드 실행 중 예외가 발생할 경우 발생
     */
    @Around("@annotation(com.serverbe.infrastructure.common.logging.Timer)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!log.isInfoEnabled()) {
            return joinPoint.proceed();
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();

            String methodName = joinPoint.getSignature().getName();
            String className = joinPoint.getSignature().getDeclaringTypeName();

            log.info("[TIMER] {}.{} | Duration: {}ms", className, methodName, stopWatch.getTotalTimeMillis());
        }
    }
}
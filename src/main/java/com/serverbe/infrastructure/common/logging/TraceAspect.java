package com.serverbe.infrastructure.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @responsibility <b>@Trace</b> 어노테이션이 부여된 클래스 내 모든 메서드의 실행 흐름을 추적하고 시각적으로 정렬된 로그를 남기는 <b>Aspect</b>입니다.
 * @implSpec 메서드 진입(ENTRY), 정상 종료(EXIT), 예외 발생(EXCEPTION) 시점을 가로채어 실행 인자와 결과물을 기록합니다.
 */
@Slf4j
@Aspect
@Component
public class TraceAspect {

    /** 로그 태그([ENTRY], [EXIT] 등)의 고정 너비 */
    private final int tagWidth = 11;
    /** 클래스 및 메서드 위치 정보의 고정 너비 */
    private final int locationWidth = 45;

    /**
     * @responsibility 대상 메서드의 실행 전후 및 예외 발생 시점을 추적하여 정규화된 포맷으로 로그를 출력합니다.
     * @implNote
     * 1. <b>진입</b>: 메서드 인자(Args)를 포함하여 기록합니다.<br>
     * 2. <b>종료</b>: 반환 값(Return)을 포함하여 기록합니다.<br>
     * 3. <b>예외</b>: 발생한 예외의 클래스명을 기록한 후 예외를 다시 던집니다.
     * @param joinPoint 실행 지점 정보를 담은 {@link ProceedingJoinPoint}
     * @return 대상 메서드의 실행 결과 객체
     * @throws Throwable 메서드 실행 중 발생하는 모든 예외
     */
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

    /**
     * @responsibility 로그의 가독성을 위해 태그와 위치 정보를 고정 너비로 포맷팅합니다.
     * @param tag 로그 범주 ([ENTRY], [EXIT], [EXCEPTION])
     * @param location 클래스명과 메서드명이 결합된 문자열
     * @return 포맷팅된 접두사 문자열
     */
    private String formatLogPrefix(String tag, String location) {
        String formattedTag = String.format("%-" + tagWidth + "s", tag);
        String formattedLocation = String.format("%-" + locationWidth + "s", location);
        return formattedTag + " " + formattedLocation;
    }
}
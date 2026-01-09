package com.serverbe.infrastructure.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;

/**
 * @responsibility Logback 로그 출력 시 특정 태그에 <b>ANSI 색상</b>을 적용하여 콘솔 가독성을 높이는 컨버터입니다.
 * @implSpec {@link ClassicConverter}를 확장하며, {@link TraceAspect}에서 생성된 주요 태그들을 감지하여 색상을 입힙니다.
 */
public class TraceColorConverter extends ClassicConverter {

    /**
     * @responsibility 로그 이벤트의 메시지를 분석하여 [ENTRY], [EXIT], [EXCEPTION] 태그에 각각 초록색, 파란색, 빨간색을 적용합니다.
     * @implNote {@link AnsiOutput}을 사용하여 운영체제 환경에 맞는 ANSI 코드를 생성하며, 메시지가 null인 경우 빈 문자열을 반환합니다.
     * @param event 로그 정보가 담긴 {@link ILoggingEvent}
     * @return 색상 코드가 삽입된 포맷팅된 로그 메시지
     */
    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) return "";

        // 1. 기존 트레이스 로그 색상
        message = message
                .replace("[ENTRY]", AnsiOutput.toString(AnsiColor.GREEN, "[ENTRY]", AnsiColor.DEFAULT))
                .replace("[EXIT]", AnsiOutput.toString(AnsiColor.BLUE, "[EXIT]", AnsiColor.DEFAULT))
                .replace("[EXCEPTION]", AnsiOutput.toString(AnsiColor.RED, "[EXCEPTION]", AnsiColor.DEFAULT));

        return message;
    }
}
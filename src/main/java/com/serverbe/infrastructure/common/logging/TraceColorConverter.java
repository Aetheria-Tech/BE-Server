package com.serverbe.infrastructure.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;

public class TraceColorConverter extends ClassicConverter {
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
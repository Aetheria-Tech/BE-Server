package com.serverbe.adapter.out.notification.dto;

public record SsePubSubMessage(
        String taskId,
        String eventName, // "COMPLETED" 또는 "FAILED"
        String data       // S3 URI 또는 에러 메시지
) {
}
package com.serverbe.adapter.in.web.dto.sqs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SageMaker Async Inference 완료 시 발행되는 이벤트 규격
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SageMakerNotificationDto(
        String invocationStatus, // "Completed" 또는 "Failed"
        String failureReason,
        ResponseParameters responseParameters
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseParameters(
            String contentType,
            String outputLocation // ex: "s3://my-bucket/output/123e4567-e89b-12d3.out"
    ) {}
}
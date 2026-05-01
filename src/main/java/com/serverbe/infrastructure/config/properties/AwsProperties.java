package com.serverbe.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 파일의 'aws' 하위 설정값들을
 * 타입 세이프(Type-safe)하게 자바 객체로 매핑해주는 Record 클래스입니다.
 */
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        S3 s3,                  // AWS S3 관련 설정 그룹
        SageMaker sagemaker,    // AWS SageMaker 관련 설정 그룹
        Sqs sqs
) {
    /**
     * S3 스토리지 관련 설정
     * 매핑 경로: aws.s3.*
     */
    public record S3(
            /*
             * AI 모델이 사용할 데이터가 저장되는 S3 버킷의 실제 이름입니다.
             * (예: my-project-ai-inference-input-bucket)
             * YAML 프로퍼티: aws.s3.ai-input-bucket
             */
            String aiInputBucket
    ) {}

    /**
     * SageMaker AI 모델 서버 관련 설정
     * 매핑 경로: aws.sagemaker.*
     */
    public record SageMaker(
            /*
             * 비동기 추론(Async Inference) 요청을 보낼 대상 SageMaker 엔드포인트의 이름입니다.
             * (예: my-project-async-inference-endpoint)
             * YAML 프로퍼티: aws.sagemaker.endpoint-name
             */
            String endpointName
    ) {}

    /**
     * SQS 관련 설정
     * 매핑 경로: aws.sqs.*
     */
    public record Sqs(
            /*
             * AI 작업 완료 알림을 수신할 SQS 큐의 이름입니다.
             * YAML 프로퍼티: aws.sqs.ai-notification-queue-name
             */
            String aiNotificationQueueName
    ) {}
}
package com.serverbe.adapter.out.external.s3;

import com.serverbe.application.port.out.s3.S3AiInputPort;
import com.serverbe.domain.exception.s3.S3ErrorCode;
import com.serverbe.domain.exception.s3.S3Exception;
import com.serverbe.infrastructure.config.properties.AwsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AI 추론(Inference)을 위한 요청 데이터(Prompt JSON)를 AWS S3에 업로드하는 외부 서비스 어댑터 구현체.
 * <p>
 * <b>역할 및 책임 (Responsibility):</b><br>
 * 애플리케이션에서 생성된 AI 작업 요청 파라미터를 S3 버킷에 저장하여,
 * 비동기로 동작하는 AI 워커(예: AWS SageMaker, GPU 인스턴스 등)가 해당 데이터를 읽어갈 수 있도록 브릿지 역할을 수행합니다.
 * </p>
 * <p>
 * <b>구현 세부사항 (Implementation Spec):</b><br>
 * AWS SDK v2의 {@link S3Client}를 사용하여 동기적으로 업로드를 수행하며,
 * 타 서비스(AI 워커)에서 즉시 참조할 수 있도록 표준 S3 URI 포맷(s3://...)을 반환합니다.
 * </p>
 */
@Slf4j
@Component
public class S3AiInputAdapter implements S3AiInputPort {

    private final S3Client s3Client;
    private final String inputBucketName;

    /**
     * S3AiInputAdapter 생성자.
     * 외부 프로퍼티 클래스에서 필요한 S3 버킷 이름만 추출하여 초기화합니다.
     *
     * @param s3Client      AWS SDK v2 S3 클라이언트 빈
     * @param awsProperties AWS 관련 설정 정보를 담고 있는 프로퍼티 객체
     */
    public S3AiInputAdapter(S3Client s3Client, AwsProperties awsProperties) {
        this.s3Client = s3Client;
        this.inputBucketName = awsProperties.s3().aiInputBucket();
    }

    /**
     * AI 모델에게 전달할 프롬프트(JSON) 데이터를 S3 버킷에 업로드합니다.
     * <p>
     * <b>구현 참고 (Implementation Note):</b><br>
     * S3 내에서 파일명 충돌(Overwrite)을 완벽하게 방지하기 위해,
     * 고유하게 발급된 {@code taskId}를 S3 Object Key의 파일명으로 사용합니다.
     * </p>
     *
     * @param taskId     AI 작업을 식별하는 고유 ID (UUID 형태). 이 값이 S3 파일명으로 사용됩니다.
     * @param promptJson AI에게 전달할 요청 파라미터가 직렬화된 JSON 문자열
     * @return 업로드 완료 후 생성된 파일의 전체 S3 URI (예: {@code s3://my-bucket/inputs/123e4567.json})
     * @throws S3Exception S3 네트워크 통신 오류나 권한 문제 등으로 업로드에 실패한 경우 발생
     */
    @Override
    public String uploadInputJson(String taskId, String promptJson) {
        // S3에 저장될 파일 경로 및 이름 (예: inputs/123e4567-e89b-12d3...json)
        String objectKey = "inputs/" + taskId + ".json";

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(inputBucketName)
                .key(objectKey)
                .contentType("application/json") // AI 워커가 명확히 JSON임을 인지하도록 MIME 타입 명시
                .build();

        try {
            // S3에 문자열(JSON) 데이터를 바로 업로드
            s3Client.putObject(putObjectRequest, RequestBody.fromString(promptJson));

            // SageMaker 등 외부 AI 플랫폼이 읽을 수 있는 표준 S3 URI 형식으로 조합하여 반환
            String s3Uri = String.format("s3://%s/%s", inputBucketName, objectKey);
            log.info("[S3 Upload] AI 비동기 입력 데이터 업로드 완료: {}", s3Uri);
            return s3Uri;

        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            log.error("[S3 Upload Error] S3 서버 거절/오류 - TaskID: {}, 상태코드: {}, 원인: {}",
                    taskId, e.statusCode(), e.awsErrorDetails().errorMessage());
            throw new S3Exception(S3ErrorCode.S3_UPLOAD_ERROR, "S3 서버 에러: " + e.awsErrorDetails().errorMessage());
        } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
            log.error("[S3 Upload Error] S3 네트워크 타임아웃 및 통신 실패 - TaskID: {}, 원인: {}",
                    taskId, e.getMessage());
            throw new S3Exception(S3ErrorCode.S3_UPLOAD_ERROR, "S3 네트워크 에러: " + e.getMessage());
        } catch (Exception e) {
            log.error("[S3 Upload Error] 알 수 없는 시스템 오류 - TaskID: {}, 원인: {}", taskId, e.getMessage());
            throw new S3Exception(S3ErrorCode.S3_UPLOAD_ERROR, "알 수 없는 S3 업로드 오류");
        }
    }
}
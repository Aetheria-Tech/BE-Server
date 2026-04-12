package com.serverbe.adapter.out.external.s3;

import com.serverbe.application.port.out.s3.S3AiInputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * @responsibility AI 추론을 위한 요청 데이터(JSON)를 S3에 업로드하는 어댑터
 * @implSpec AWS SDK v2의 S3Client를 사용하여 동기적으로 업로드하며, 업로드된 파일의 S3 URI를 반환합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3AiInputAdapter implements S3AiInputPort {

    private final S3Client s3Client;

    // 인프라 환경(CDK)에서 설정한 S3 Input 버킷 이름
    @Value("${aws.s3.ai-input-bucket}")
    private String inputBucketName;

    /**
     * @param taskId     발급된 고유 Task ID (UUID)
     * @param promptJson AI에게 전달할 요청 파라미터 (JSON 문자열)
     * @return 업로드된 파일의 S3 URI (예: s3://my-bucket/inputs/uuid.json)
     * @implNote 파일명 충돌을 막기 위해 taskId를 S3 Object Key로 사용합니다.
     */
    @Override
    public String uploadInputJson(String taskId, String promptJson) {
        // S3에 저장될 파일 경로 및 이름 (예: inputs/123e4567-e89b-12d3...json)
        String objectKey = "inputs/" + taskId + ".json";

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(inputBucketName)
                .key(objectKey)
                .contentType("application/json")
                .build();

        try {
            // S3에 JSON 데이터 업로드
            s3Client.putObject(putObjectRequest, RequestBody.fromString(promptJson));
            
            // SageMaker가 읽을 수 있는 S3 URI 형식으로 조합하여 반환
            String s3Uri = String.format("s3://%s/%s", inputBucketName, objectKey);
            log.info("[S3 Upload] AI 비동기 입력 데이터 업로드 완료: {}", s3Uri);
            return s3Uri;
            
        } catch (Exception e) {
            log.error("[S3 Upload Error] 입력 데이터 업로드 실패 - TaskID: {}, 원인: {}", taskId, e.getMessage());
            throw new RuntimeException("S3 입력 버킷 업로드 중 오류가 발생했습니다.", e);
        }
    }
}
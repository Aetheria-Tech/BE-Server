package com.serverbe.adapter.out.external.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;
import com.serverbe.application.port.out.s3.S3AiOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3AiOutputAdapter implements S3AiOutputPort {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    /**
     * @param s3Uri 조회할 S3 경로
     * @return 파일이 존재하면 JSON을 파싱한 DTO 반환, 없으면 empty
     */
    @Override
    public Optional<AiGenerationResultDto> downloadOutput(String s3Uri) {
        String bucket = s3Uri.replace("s3://", "").split("/")[0];
        String key = s3Uri.substring(s3Uri.indexOf(bucket) + bucket.length() + 1);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            // S3에서 Stream으로 데이터를 가져옴
            ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(request);

            // ✨ Stream을 바로 JSON DTO로 변환 (메모리 효율적)
            AiGenerationResultDto resultDto = objectMapper.readValue(s3ObjectStream, AiGenerationResultDto.class);

            return Optional.of(resultDto);

        } catch (NoSuchKeyException e) {
            return Optional.empty(); // 아직 작업 중임
        } catch (Exception e) {
            log.error("[S3 Download Error] 결과 JSON 읽기/파싱 실패: {}", s3Uri, e);
            throw new RuntimeException("S3 결과 다운로드 및 파싱 중 오류 발생", e);
        }
    }
}
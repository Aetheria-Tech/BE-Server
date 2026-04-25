package com.serverbe.adapter.out.external.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;
import com.serverbe.application.port.out.s3.S3AiOutputPort;
import com.serverbe.domain.exception.s3.S3ErrorCode;
import com.serverbe.domain.exception.s3.S3Exception;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Optional;

/**
 * AI 생성 결과물(JSON)을 AWS S3에서 다운로드하여 내부 DTO로 변환하는 외부 서비스 어댑터 구현체.
 * <p>
 * AI 모델(또는 외부 워커)이 S3에 비동기적으로 결과물을 업로드하면,
 * 이 클래스가 해당 S3 객체를 조회하여 메모리 효율적인 스트림(Stream) 방식으로 즉시 역직렬화(파싱)합니다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3AiOutputAdapter implements S3AiOutputPort {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    /**
     * S3 URI 경로를 기반으로 AI 생성 결과 JSON 파일을 다운로드하여 DTO 객체로 반환합니다.
     * <p>
     * <b>주요 처리 로직:</b><br>
     * 1. <b>URI 파싱:</b> {@code s3://bucket-name/key} 형태의 문자열에서 버킷명과 객체 키(Key)를 추출합니다.<br>
     * 2. <b>스트림 파싱:</b> S3에서 제공하는 {@link ResponseInputStream}을 {@link ObjectMapper}에 직접 넘겨주어, 대용량 파일이어도 메모리 부담 없이 효율적으로 JSON을 객체화합니다.<br>
     * 3. <b>비동기 상태 처리:</b> 아직 AI 작업이 완료되지 않아 S3에 파일이 없는 경우({@link NoSuchKeyException}), 에러를 발생시키지 않고 {@link Optional#empty()}를 반환하여 '작업 진행 중' 상태임을 알립니다.
     * </p>
     *
     * @param s3Uri 조회할 S3의 전체 경로 (예: "s3://my-bucket/ai-outputs/result-123.json")
     * @return S3 객체가 존재하고 파싱에 성공하면 데이터를 담은 {@link Optional}, 객체가 없으면 {@link Optional#empty()}
     * @throws S3Exception S3 통신 장애 또는 JSON 데이터 구조가 맞지 않아 파싱에 실패할 경우
     */
    @Override
    public Optional<AiGenerationResultDto> downloadOutput(String s3Uri) {
        // java.net.URI를 활용하여 안전하고 직관적인 버킷/키 추출
        java.net.URI uri = java.net.URI.create(s3Uri);
        String bucket = uri.getHost();
        String path = uri.getPath();

        // getPath()는 시작 부분에 '/'를 포함하므로 제거합니다. (경로가 비어있는 엣지 케이스 방어)
        String key = (path != null && path.length() > 1) ? path.substring(1) : "";

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            // S3에서 데이터를 Stream 형태로 가져옵니다.
            ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(request);

            // Stream을 String 등으로 변환하여 메모리에 올리지 않고, 바로 JSON DTO로 변환하여 성능을 최적화합니다.
            AiGenerationResultDto resultDto = objectMapper.readValue(s3ObjectStream, AiGenerationResultDto.class);

            return Optional.of(resultDto);

        } catch (NoSuchKeyException e) {
            // 해당 키(파일)가 없다는 것은 아직 AI 작업이 끝나지 않았다는 의미이므로, 빈 Optional을 반환합니다.
            return Optional.empty();
        } catch (Exception e) {
            // 그 외 통신 에러나 JSON 파싱 에러(MismatchedInputException 등)는 시스템 예외로 처리합니다.
            log.error("[S3 Download Error] 결과 JSON 읽기/파싱 실패: {}", s3Uri, e);
            throw new S3Exception(S3ErrorCode.S3_DOWNLOAD_ERROR, e.getMessage());
        }
    }
}
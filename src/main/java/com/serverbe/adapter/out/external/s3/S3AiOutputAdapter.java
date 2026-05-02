package com.serverbe.adapter.out.external.s3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;
import com.serverbe.application.port.out.s3.S3AiOutputPort;
import com.serverbe.domain.exception.s3.S3ErrorCode;
import com.serverbe.domain.exception.s3.S3Exception;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.net.URI;
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
        BucketAndKey bucketAndKey = BucketAndKey.fromUri(s3Uri);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketAndKey.bucket())
                    .key(bucketAndKey.key())
                    .build();

            ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(request);
            AiGenerationResultDto resultDto = objectMapper.readValue(s3ObjectStream, AiGenerationResultDto.class);

            return Optional.of(resultDto);

            // 1. 정상적인 비즈니스 흐름: 아직 AI 작업이 안 끝남 (파일 없음)
        } catch (NoSuchKeyException e) {
            return Optional.empty();

            // 2. AWS S3 서버 측 에러 (권한 부족 403, 서버 오류 500 등)
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            log.error("[S3 Download Error] S3 서버 거절/오류 - URI: {}, 상태코드: {}, 원인: {}",
                    s3Uri, e.statusCode(), e.awsErrorDetails().errorMessage());
            throw new S3Exception(S3ErrorCode.S3_DOWNLOAD_ERROR, "S3 서버 에러: " + e.awsErrorDetails().errorMessage());

            // 3. 네트워크 단절, 타임아웃 등 클라이언트 측 에러
        } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
            log.error("[S3 Download Error] S3 네트워크 타임아웃 및 통신 실패 - URI: {}, 원인: {}",
                    s3Uri, e.getMessage());
            throw new S3Exception(S3ErrorCode.S3_DOWNLOAD_ERROR, "S3 네트워크 에러: " + e.getMessage());

            // 4. JSON 파싱 에러 (AI가 내려준 결과물이 규격에 맞지 않거나 깨진 경우)
        } catch (java.io.IOException e) {
            log.error("[S3 Download Error] 결과 JSON 읽기/파싱 실패 (데이터 포맷 오류) - URI: {}, 원인: {}",
                    s3Uri, e.getMessage());
            throw new S3Exception(S3ErrorCode.S3_DOWNLOAD_ERROR, "JSON 파싱 에러: " + e.getMessage());

            // 5. 기타 런타임 에러
        } catch (Exception e) {
            log.error("[S3 Download Error] 알 수 없는 시스템 오류 - URI: {}, 원인: {}", s3Uri, e.getMessage());
            throw new S3Exception(S3ErrorCode.S3_DOWNLOAD_ERROR, "알 수 없는 S3 다운로드 오류");
        }
    }

    /**
     * 작업이 완료되어 더 이상 필요 없는 S3 결과물(JSON) 파일을 즉시 삭제하여 스토리지 비용을 최적화합니다.
     * <p>
     * <b>장애 격리 (Fault Isolation):</b><br>
     * 삭제 로직이 실패하더라도(S3 권한 에러 등), 이미 DB에 데이터가 저장된 핵심 비즈니스 로직이나
     * 클라이언트에게 알림을 발송하는 후속 파이프라인이 중단되지 않도록 예외를 던지지 않고 에러 로그만 남깁니다.
     * (만약 삭제되지 않은 찌꺼기 파일이 남더라도, S3 Lifecycle 정책을 통해 1일 후 자동 삭제되도록 방어막을 구축합니다.)
     * </p>
     *
     * @param s3Uri 삭제할 S3의 전체 경로
     */
    @Override
    public void deleteOutput(String s3Uri) {
        BucketAndKey bucketAndKey = BucketAndKey.fromUri(s3Uri);

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketAndKey.bucket())
                    .key(bucketAndKey.key())
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("[S3 Delete] 비용 최적화 - S3 객체 삭제 완료: {}", s3Uri);

            // 삭제 작업은 예외를 던지지 않고 격리(Fault Isolation)하되, 로그는 원인별로 명확하게 남깁니다.
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            log.error("[S3 Delete Error] S3 서버 권한/오류로 삭제 실패 (Lifecycle 정책 대기) - URI: {}, 상태코드: {}", s3Uri, e.statusCode());
        } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
            log.error("[S3 Delete Error] S3 네트워크 오류로 삭제 실패 (Lifecycle 정책 대기) - URI: {}, 원인: {}", s3Uri, e.getMessage());
        } catch (Exception e) {
            log.error("[S3 Delete Error] 알 수 없는 오류로 삭제 실패 (Lifecycle 정책 대기) - URI: {}, 원인: {}", s3Uri, e.getMessage());
        }
    }

    /**
    * @responsibility 버킷과 키를 담기 위한 DTO
    * */
    private record BucketAndKey(
            String bucket,
            String key
    ){
        public static BucketAndKey fromUri(String s3Uri) {
            // 1. 방어적 프로그래밍 (파라미터 검증)
            Assert.isTrue(StringUtils.hasText(s3Uri), "s3Uri는 null이거나 비어있을 수 없습니다.");
            Assert.isTrue(s3Uri.startsWith("s3://"), () -> "유효하지 않은 S3 URI 형식입니다. (s3:// 로 시작해야 함): " + s3Uri);

            // 2. 파싱 로직 (검증이 끝났으므로 URI.create는 안전하게 동작함)
            URI uri = URI.create(s3Uri);
            String bucket = uri.getHost();
            Assert.hasText(bucket, () -> "S3 URI에서 버킷명을 추출할 수 없습니다: " + s3Uri);
            String path = uri.getPath();

            // 3. Key 추출 및 객체 생성
            String key = (path != null && path.length() > 1) ? path.substring(1) : "";
            return new BucketAndKey(bucket, key);
        }
    }
}
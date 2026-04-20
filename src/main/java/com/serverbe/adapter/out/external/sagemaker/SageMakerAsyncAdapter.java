package com.serverbe.adapter.out.external.sagemaker;

import com.serverbe.application.port.out.sagemaker.SageMakerAsyncPort;
import com.serverbe.domain.exception.sagemaker.SageMakerErrorCode;
import com.serverbe.domain.exception.sagemaker.SageMakerException;
import com.serverbe.infrastructure.config.properties.AwsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncResponse;

/**
 * @responsibility AWS SageMaker 비동기 엔드포인트(Async Endpoint)를 호출하는 어댑터
 * @implSpec AWS SDK v2를 활용하여 S3 Input URI를 전달하고, SageMaker의 추론을 비동기로 트리거합니다.
 */
@Slf4j
@Component
public class SageMakerAsyncAdapter implements SageMakerAsyncPort {

    private final SageMakerRuntimeClient sageMakerClient;
    private final String endpointName;

    public SageMakerAsyncAdapter(SageMakerRuntimeClient sageMakerClient, AwsProperties awsProperties) {
        this.sageMakerClient = sageMakerClient;
        this.endpointName = awsProperties.sagemaker().endpointName();
    }

    /**
     * @param inputS3Uri S3에 업로드된 요청 데이터의 URI
     * @return 추후 결과물이 저장될 S3 Output URI (SageMaker가 응답으로 알려줌)
     * @implNote 비동기 호출이므로 결과를 기다리지 않고 HTTP 202 Accepted 수준의 응답만 즉시 리턴받습니다.
     */
    @Override
    public String invokeAsync(String inputS3Uri) {
        InvokeEndpointAsyncRequest request = InvokeEndpointAsyncRequest.builder()
                .endpointName(endpointName)
                .inputLocation(inputS3Uri) // 읽어야 할 S3 경로
                .contentType("application/json") // S3에 있는 파일의 타입
                .build();

        try {
            InvokeEndpointAsyncResponse response = sageMakerClient.invokeEndpointAsync(request);
            
            // SageMaker 비동기 엔드포인트는 호출 직후 결과물이 저장될 예정인 S3 URI를 미리 알려줍니다.
            String outputLocation = response.outputLocation();
            log.info("[SageMaker Invoke] 비동기 추론 요청 성공 - 예상 Output 위치: {}", outputLocation);
            return outputLocation;

        } catch (Exception e) {
            log.error("[SageMaker Error] 엔드포인트 호출 실패 - Input: {}, 원인: {}", inputS3Uri, e.getMessage());
            throw new SageMakerException(SageMakerErrorCode.SAGE_MAKER_ERROR_CODE, e.getMessage());
        }
    }
}
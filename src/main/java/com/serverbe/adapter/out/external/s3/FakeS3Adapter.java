package com.serverbe.adapter.out.external.s3;

import com.serverbe.application.port.out.s3.S3AiInputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary // 로컬 환경에서는 실제 S3 어댑터 대신 이 가짜 빈을 최우선으로 주입받게 합니다.
@Profile({"local", "test", "dev"}) // 🚨 상용(prod)에서는 절대 활성화되지 않음
public class FakeS3Adapter implements S3AiInputPort {

    @Override
    public String uploadInputJson(String taskId, String promptJson) {
        log.info("[MOCK] S3 업로드 시뮬레이션 작동 중...");
        log.info("[MOCK] Task ID: {}", taskId);
        log.info("[MOCK] 업로드할 프롬프트 JSON: {}", promptJson);

        // 실제 S3에 올리지 않고, 가짜 S3 경로(URI)를 즉시 반환합니다.
        String dummyS3Uri = "s3://mock-project-bucket/ai-input/" + taskId + ".json";
        log.info("[MOCK] 생성된 가짜 Input S3 URI 반환: {}", dummyS3Uri);
        
        return dummyS3Uri;
    }
}
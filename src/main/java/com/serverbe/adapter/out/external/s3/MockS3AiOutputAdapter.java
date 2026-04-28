package com.serverbe.adapter.out.external.s3;

import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;
import com.serverbe.application.port.out.s3.S3AiOutputPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@Primary // 💡 동일한 인터페이스 구현체가 2개일 때, 이 녀석을 최우선으로 주입하라는 뜻입니다.
@Profile({"local", "dev"}) // 💡 로컬이나 개발 환경(Profile)에서만 이 Mock 빈이 활성화됩니다.
public class MockS3AiOutputAdapter implements S3AiOutputPort {

    @Override
    public Optional<AiGenerationResultDto> downloadOutput(String s3Uri) {
        log.info("[MOCK S3] 진짜 S3 대신 가짜 데이터를 반환합니다! 다운로드 요청 URI: {}", s3Uri);

        // 실제 S3에 접속하지 않고, DB와 Redis GEO에 들어갈 완벽한 가짜(Mock) 데이터를 조립합니다.
        AiGenerationResultDto mockResult = new AiGenerationResultDto(
                37.5665, // startLat (예: 서울)
                126.9780, // startLon
                5000.0, // distance (5km)
                "}obwEu{|eW_ibE_ibE~hbE~hbE" // gpx 또는 polyline (가짜 데이터)
        );

        // 마치 S3에서 다운로드에 성공한 것처럼 Optional로 감싸서 반환
        return Optional.of(mockResult);
    }

    @Override
    public void deleteOutput(String s3Uri) throws InterruptedException {
        // 실제 AWS API를 찌르지 않고 삭제되었다고 로그만 남깁니다.
        log.info("[MOCK S3] 🗑️ S3 데이터 삭제 요청 수신! (실제로는 삭제되지 않습니다) 삭제 대상 URI: {}", s3Uri);

        // 여기에 Thread.sleep(100); 등을 넣어서 약간의 딜레이를 주면
        // 네트워크 I/O를 더욱 현실감 있게 시뮬레이션할 수도 있습니다.
        Thread.sleep(100);
    }
}
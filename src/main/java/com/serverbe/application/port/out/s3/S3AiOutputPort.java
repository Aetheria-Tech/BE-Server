package com.serverbe.application.port.out.s3;

import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;

import java.util.Optional;

public interface S3AiOutputPort {
    Optional<AiGenerationResultDto> downloadOutput(String s3Uri);

    /**
     * S3 비용 최적화를 위해 사용 완료된 객체를 삭제합니다.
     *
     * @param s3Uri 삭제할 S3 객체의 전체 URI (또는 Object Key)
     */
    void deleteOutput(String s3Uri);
}
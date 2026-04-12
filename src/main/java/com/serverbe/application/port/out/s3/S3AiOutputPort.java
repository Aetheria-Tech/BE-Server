package com.serverbe.application.port.out.s3;

import com.serverbe.application.port.in.dto.art.AiGenerationResultDto;

import java.util.Optional;

public interface S3AiOutputPort {
    Optional<AiGenerationResultDto> downloadOutput(String s3Uri);
}
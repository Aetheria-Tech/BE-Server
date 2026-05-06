package com.serverbe.application.port.out.s3;

public interface S3AiInputPort {
    String uploadInputJson(String taskId, String promptJson);
    void deleteInputFile(String s3Uri);
}
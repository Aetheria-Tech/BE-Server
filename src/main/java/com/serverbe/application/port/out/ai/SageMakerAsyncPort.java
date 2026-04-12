package com.serverbe.application.port.out.ai;

public interface SageMakerAsyncPort {
    String invokeAsync(String inputS3Uri);
}
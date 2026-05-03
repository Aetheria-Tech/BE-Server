package com.serverbe.application.port.out.sagemaker;

public interface SageMakerAsyncPort {
    String invokeAsync(String inputS3Uri);
}
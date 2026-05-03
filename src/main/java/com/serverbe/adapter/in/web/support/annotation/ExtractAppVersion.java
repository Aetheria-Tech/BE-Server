package com.serverbe.adapter.in.web.support.annotation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(
        name = "X-App-Version",
        description = "클라이언트(앱) 버전 정보. 모바일 앱 등에서 전송하는 경우 사용됩니다.",
        in = ParameterIn.HEADER,
        required = false,
        hidden = true
)
public @interface ExtractAppVersion {
}
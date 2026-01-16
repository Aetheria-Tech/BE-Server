package com.serverbe.adapter.in.web.support.annotation;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(
        name = "Authorization",
        description = "애플리케이션에게 인증 정보를 보내는데 사용됩니다",
        in = ParameterIn.HEADER,
        required = false,
        schema = @Schema(type = "string", example = "Bearer eyJhbGciOiJIUz..."),
        hidden = true
)
public @interface ExtractAccessToken {
}
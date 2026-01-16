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
        name = "aetheria-ref",
        description = "클라이언트가 액세스 토큰을 갱신하기 위해서 사용합니다.",
        in = ParameterIn.COOKIE
)
public @interface ExtractRefreshToken {
}
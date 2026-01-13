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
// Swagger 문서에 헤더 명세를 추가합니다. (필요시 hidden=true로 변경 가능)
@Parameter(
        name = "X-App-Version",
        description = "클라이언트(앱) 버전 정보. 모바일 앱 등에서 전송하는 경우 사용됩니다.",
        in = ParameterIn.HEADER,
        required = false,
        schema = @Schema(type = "string", example = "1.0.0"),
        hidden = true
)
public @interface ExtractAppVersion {
}
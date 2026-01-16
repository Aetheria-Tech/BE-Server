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
        name = "User-Agent",
        description = "클라이언트 브라우저/기기 정보. X-Device-Id가 없을 경우, 이 값을 해싱하여 기기 식별자로 사용합니다. (브라우저는 자동 전송)",
        in = ParameterIn.HEADER,
        example = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
public @interface ExtractDeviceId {
}
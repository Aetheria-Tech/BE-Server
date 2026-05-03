package com.serverbe.adapter.in.web.support.resolver;

import com.serverbe.adapter.in.web.support.annotation.ExtractDeviceId;
import com.serverbe.infrastructure.util.DeviceUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @responsibility {@link ExtractDeviceId} 애노테이션이 붙은 컨트롤러에서 디바이스 아이디를 가져오도록 합니다.
 * */
@Component
public class DeviceIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 파라미터에 @ExtractDeviceId 어노테이션이 있고, 타입이 String인지 확인
        return parameter.hasParameterAnnotation(ExtractDeviceId.class)
                && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        return DeviceUtils.extractDeviceId(request);
    }
}
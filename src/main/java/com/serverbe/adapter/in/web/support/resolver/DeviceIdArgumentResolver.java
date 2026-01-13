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

@Component
public class DeviceIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 파라미터에 @ExtractDeviceId 어노테이션이 있고, 타입이 String인지 확인
        return parameter.hasParameterAnnotation(ExtractDeviceId.class)
               && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        
        // 기존에 만들어둔 DeviceUtils 활용
        return DeviceUtils.extractDeviceId(request);
    }
}
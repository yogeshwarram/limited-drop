package com.limiteddrop.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentCustomerArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentCustomer.class)
                && parameter.getParameterType().equals(AuthenticatedCustomer.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        AuthenticatedCustomer customer = request == null ? null : (AuthenticatedCustomer) request.getAttribute(AuthenticatedCustomerInterceptor.REQUEST_ATTRIBUTE);
        if (customer == null) throw new IllegalStateException("Authenticated customer context is missing");
        return customer;
    }
}

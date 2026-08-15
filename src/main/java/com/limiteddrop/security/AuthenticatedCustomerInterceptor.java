package com.limiteddrop.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * An MVC pre-handle adapter, not a JWT validator. The security filter chain has
 * already verified the token before this interceptor can run.
 */
public class AuthenticatedCustomerInterceptor implements HandlerInterceptor {
    public static final String REQUEST_ATTRIBUTE = AuthenticatedCustomer.class.getName();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)
                || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "An authenticated JWT subject is required");
            return false;
        }
        request.setAttribute(REQUEST_ATTRIBUTE, new AuthenticatedCustomer(jwt.getSubject()));
        return true;
    }
}

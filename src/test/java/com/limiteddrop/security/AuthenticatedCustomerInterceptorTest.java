package com.limiteddrop.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedCustomerInterceptorTest {
    private final AuthenticatedCustomerInterceptor interceptor = new AuthenticatedCustomerInterceptor();
    private final CurrentCustomerArgumentResolver resolver = new CurrentCustomerArgumentResolver();

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void adaptsAValidatedJwtSubjectIntoCustomerRequestContext() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice").build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        authentication.setAuthenticated(true); // mirrors the resource-server filter's post-validation context
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute(AuthenticatedCustomerInterceptor.REQUEST_ATTRIBUTE)).isEqualTo(new AuthenticatedCustomer("alice"));

        MethodParameter parameter = currentCustomerParameter();
        assertThat(resolver.supportsParameter(parameter)).isTrue();
        assertThat(resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null)).isEqualTo(new AuthenticatedCustomer("alice"));
    }

    @Test
    void rejectsARequestWithoutAuthenticatedJwtContext() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private MethodParameter currentCustomerParameter() throws NoSuchMethodException {
        Method method = AuthenticatedCustomerInterceptorTest.class.getDeclaredMethod("endpoint", AuthenticatedCustomer.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void endpoint(@CurrentCustomer AuthenticatedCustomer customer) { }
}

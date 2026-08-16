package com.limiteddrop.security;

import com.limiteddrop.config.ReservationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ReservationProperties properties) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/dev/tokens").access((authentication, context) ->
                                new org.springframework.security.authorization.AuthorizationDecision(properties.getSecurity().isDevTokenEndpointEnabled()))
                        .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_drops:manage")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(ReservationProperties properties) {
        var security = properties.getSecurity();
        if (!hasText(security.getAudience())) throw new IllegalStateException("Configure APP_SECURITY_AUDIENCE");
        if (hasText(security.getIssuerUri())) {
            NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(security.getIssuerUri());
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(security.getIssuerUri()), audienceValidator(security.getAudience())));
            return decoder;
        }
        if (hasText(security.getJwkSetUri())) {
            if (!hasText(security.getExpectedIssuer())) {
                throw new IllegalStateException("Configure APP_SECURITY_EXPECTED_ISSUER when using APP_SECURITY_JWK_SET_URI");
            }
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(security.getJwkSetUri()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(security.getExpectedIssuer()), audienceValidator(security.getAudience())));
            return decoder;
        }
        if (!hasText(security.getHmacSecret()) || security.getHmacSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Configure APP_SECURITY_ISSUER_URI, APP_SECURITY_JWK_SET_URI, or a >=32 byte APP_SECURITY_HMAC_SECRET");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(security.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), audienceValidator(security.getAudience())));
        return decoder;
    }

    @Bean
    AuthenticatedCustomerInterceptor authenticatedCustomerInterceptor() { return new AuthenticatedCustomerInterceptor(); }

    @Bean
    CurrentCustomerArgumentResolver currentCustomerArgumentResolver() { return new CurrentCustomerArgumentResolver(); }

    @Bean
    WebMvcConfigurer authenticatedCustomerMvcConfigurer(AuthenticatedCustomerInterceptor interceptor, CurrentCustomerArgumentResolver resolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/dev/**");
            }
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) { resolvers.add(resolver); }
        };
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator(String audience) {
        return new JwtClaimValidator<List<String>>("aud", claim -> claim != null && claim.contains(audience));
    }
}

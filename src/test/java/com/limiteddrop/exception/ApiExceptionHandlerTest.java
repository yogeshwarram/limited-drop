package com.limiteddrop.exception;

import com.limiteddrop.response.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ApiExceptionHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private ApiExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));
        request = new MockHttpServletRequest("GET", "/api/v1/test");
    }

    @Test
    void mapsNotFoundToStableApiError() {
        ApiError error = handler.notFound(new NotFoundException("Missing"), request).getBody();

        assertThat(error).isEqualTo(new ApiError(NOW, 404, "NOT_FOUND", "Missing", "/api/v1/test", Map.of()));
    }

    @Test
    void mapsConflictAndDropNotOpenStatuses() {
        assertThat(handler.conflict(new ConflictException("Conflict"), request).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.notOpen(new DropNotOpenException(), request).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void mapsMissingHeaderToBadRequest() {
        MethodParameter parameter = mock(MethodParameter.class);
        doReturn(String.class).when(parameter).getNestedParameterType();
        var exception = new org.springframework.web.bind.MissingRequestHeaderException("Idempotency-Key", parameter);
        var response = handler.missingHeader(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("MISSING_HEADER");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
    }

    @Test
    void mapsValidationFieldErrorsIncludingNullMessages() {
        BindingResult binding = mock(BindingResult.class);
        doReturn(java.util.List.of(new FieldError("request", "quantity", "must be positive"),
                new FieldError("request", "customerId", null))).when(binding).getFieldErrors();
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException((MethodParameter) null, binding);

        var response = handler.validation(exception, request);

        assertThat(response.getBody().violations()).containsEntry("quantity", "must be positive")
                .containsEntry("customerId", "invalid");
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }
}

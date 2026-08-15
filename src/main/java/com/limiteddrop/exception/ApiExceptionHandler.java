package com.limiteddrop.exception;

import com.limiteddrop.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;
    public ApiExceptionHandler(Clock clock) { this.clock = clock; }
    @ExceptionHandler(NotFoundException.class) ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest r) { return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), r, Map.of()); }
    @ExceptionHandler({ConflictException.class}) ResponseEntity<ApiError> conflict(ConflictException e, HttpServletRequest r) { return error(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), r, Map.of()); }
    @ExceptionHandler(DropNotOpenException.class) ResponseEntity<ApiError> notOpen(DropNotOpenException e, HttpServletRequest r) { return error(HttpStatus.UNPROCESSABLE_ENTITY, "DROP_NOT_OPEN", e.getMessage(), r, Map.of()); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        Map<String, String> violations = e.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(f -> f.getField(), f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(), (a, b) -> a));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", r, violations);
    }
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class) ResponseEntity<ApiError> missingHeader(Exception e, HttpServletRequest r) { return error(HttpStatus.BAD_REQUEST, "MISSING_HEADER", e.getMessage(), r, Map.of()); }
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request, Map<String, String> violations) {
        return ResponseEntity.status(status).body(new ApiError(clock.instant(), status.value(), code, message, request.getRequestURI(), violations));
    }
}

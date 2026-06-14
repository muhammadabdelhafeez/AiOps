package org.kfh.aiops.platform.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiOpsException.class)
    ResponseEntity<ProblemResponse> handleAiOps(AiOpsException ex) {
        return problem(ex.status(), ex.code(), ex.getMessage(), Map.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemResponse> handleValidation(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ProblemResponse> handleDuplicateKey(DuplicateKeyException ex) {
        return problem(HttpStatus.CONFLICT, "CONFLICT", "A record with the same unique key already exists", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "Request violates database integrity constraints", Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemResponse> handleResponseStatus(ResponseStatusException ex) {
        return problem(ex.getStatusCode(), statusCode(ex.getStatusCode()), message(ex), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", Map.of("path", ex.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Request could not be processed", Map.of("path", request.getRequestURI()));
    }

    private ResponseEntity<ProblemResponse> problem(
            HttpStatusCode status, String code, String message, Map<String, Object> details) {
        var body = new ProblemResponse(code, message, Instant.now(), correlationId(), new LinkedHashMap<>(details));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private String statusCode(HttpStatusCode statusCode) {
        return statusCode instanceof HttpStatus status ? status.name() : "HTTP_" + statusCode.value();
    }

    private String message(ResponseStatusException ex) {
        if (ex.getReason() != null && !ex.getReason().isBlank()) {
            return ex.getReason();
        }
        return ex.getStatusCode() instanceof HttpStatus status ? status.getReasonPhrase() : "Request failed";
    }

    private String correlationId() {
        var value = MDC.get("correlationId");
        return value == null || value.isBlank() ? "unavailable" : value;
    }
}


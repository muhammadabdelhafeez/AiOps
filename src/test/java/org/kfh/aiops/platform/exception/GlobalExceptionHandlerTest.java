package org.kfh.aiops.platform.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private static final String MESSAGE = "Database-backed identity storage is required to create login users";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPreserveResponseStatusExceptionStatusAndMessage() {
        MDC.put("correlationId", "corr-users-create-503");
        var handler = new GlobalExceptionHandler();

        var response = handler.handleResponseStatus(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertEquals("SERVICE_UNAVAILABLE", response.getBody().code());
        assertEquals(MESSAGE, response.getBody().message());
        assertEquals("corr-users-create-503", response.getBody().correlationId());
    }
}


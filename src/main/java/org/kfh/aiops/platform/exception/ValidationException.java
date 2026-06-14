package org.kfh.aiops.platform.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends AiOpsException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}


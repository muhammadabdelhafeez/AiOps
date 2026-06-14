package org.kfh.aiops.platform.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AiOpsException {

    public ConflictException(String message) {
        super("CONFLICT", message, HttpStatus.CONFLICT);
    }
}


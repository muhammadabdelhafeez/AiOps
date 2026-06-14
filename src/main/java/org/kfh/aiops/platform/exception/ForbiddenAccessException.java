package org.kfh.aiops.platform.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenAccessException extends AiOpsException {

    public ForbiddenAccessException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}


package org.kfh.aiops.platform.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends AiOpsException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}


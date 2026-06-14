package org.kfh.aiops.platform.exception;

import org.springframework.http.HttpStatus;

public class MissingTenantContextException extends AiOpsException {

    public MissingTenantContextException(String message) {
        super("MISSING_OR_INVALID_CONTEXT", message, HttpStatus.BAD_REQUEST);
    }
}


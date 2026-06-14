package org.kfh.aiops.platform.exception;

import org.springframework.http.HttpStatus;

/**
 * Base runtime exception with stable problem-code metadata.
 */
public class AiOpsException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public AiOpsException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}


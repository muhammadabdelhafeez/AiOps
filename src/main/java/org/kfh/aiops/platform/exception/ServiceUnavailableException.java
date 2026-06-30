package org.kfh.aiops.platform.exception;

import org.springframework.http.HttpStatus;

/** Technical dependency unavailable error mapped to HTTP 503. */
public class ServiceUnavailableException extends AiOpsException {

    public ServiceUnavailableException(String code, String message) {
        super(code, message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}


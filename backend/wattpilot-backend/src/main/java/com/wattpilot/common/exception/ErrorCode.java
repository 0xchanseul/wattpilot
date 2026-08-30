package com.wattpilot.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-level error codes returned in the {@code code} field of an error response.
 *
 * <p>Each code carries the HTTP status it maps to, keeping the transport-level status and the
 * application-level code as separate concerns. Only the shared codes are defined here; domain
 * specific codes (e.g. {@code EV_NOT_FOUND}) are added by their own modules as features are built.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "The request is malformed or invalid."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

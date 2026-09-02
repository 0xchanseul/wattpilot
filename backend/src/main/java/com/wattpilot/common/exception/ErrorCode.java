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
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access to this resource is denied."),
    // Deliberately identical for an unknown email, a wrong password and a deactivated
    // account so the response cannot be used to probe which emails are registered.
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "The token is invalid."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "The token has expired."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "This email is already registered."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found."),

    // An EV that does not exist, or exists but is owned by another account: both are reported the
    // same way so the API cannot be used to probe which EV ids exist.
    EV_NOT_FOUND(HttpStatus.NOT_FOUND, "EV not found."),

    // No stored electricity price covers the requested area and time.
    ELECTRICITY_PRICE_NOT_FOUND(HttpStatus.NOT_FOUND, "No electricity price is available for the requested area and time."),
    // The from/to query window is empty or inverted: each bound parses, but the range cannot be served.
    INVALID_TIME_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "The 'to' timestamp must be after 'from'.");

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

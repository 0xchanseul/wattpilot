package com.wattpilot.common.exception;

/**
 * Base exception for expected, business-rule failures.
 *
 * <p>Services throw this instead of building error responses directly; {@link GlobalExceptionHandler}
 * turns it into the shared error response using the associated {@link ErrorCode}.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}

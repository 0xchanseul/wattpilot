package com.wattpilot.common.exception;

import com.wattpilot.common.response.ErrorResponse;
import com.wattpilot.common.response.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Translates exceptions into the {@link ErrorResponse} contract defined in docs/openapi.yaml so that
 * controllers never build error payloads themselves.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so the built-in Spring MVC exceptions
 * (unsupported method or media type, missing parameters, unreadable body, ...) are normalised into
 * the same response shape instead of falling through to the generic 500 handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Field names whose rejected value must never be echoed back. A validation failure on a
     * password or a token would otherwise return the submitted secret in the error response,
     * where it can end up in browser consoles, proxy logs and error trackers.
     */
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of("password", "currentpassword",
            "newpassword", "passwordconfirmation", "token", "accesstoken", "refreshtoken", "secret");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.errorCode();
        log.warn("Business exception: code={}, message={}", errorCode.name(), ex.getMessage());
        return problem(errorCode.status(), errorCode.name(), ex.getMessage(), request.getRequestURI(), newTraceId(), null);
    }

    /**
     * Any exception without a more specific handler is an unexpected server error. The original
     * exception is logged with a trace id but its type, message and stack trace are never exposed.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        String traceId = newTraceId();
        log.error("Unexpected error [traceId={}]", traceId, ex);
        return problem(
                ErrorCode.INTERNAL_SERVER_ERROR.status(),
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage(),
                request.getRequestURI(),
                traceId,
                null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), rejectedValueOf(error), error.getDefaultMessage()))
                .toList();
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                path(request),
                newTraceId(),
                fieldErrors);
        return problemEntity(HttpStatus.BAD_REQUEST, headers, body);
    }

    /**
     * Fallback for the remaining Spring MVC exceptions handled by the base class: reuse the status
     * Spring already resolved and map it onto a shared {@link ErrorCode}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ErrorCode errorCode = status.is5xxServerError() ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.BAD_REQUEST;
        ErrorResponse errorResponse = ErrorResponse.of(
                status.value(),
                errorCode.name(),
                errorCode.defaultMessage(),
                path(request),
                newTraceId(),
                null);
        return problemEntity(status, headers, errorResponse);
    }

    private static ResponseEntity<ErrorResponse> problem(HttpStatus status, String code, String message, String path,
                                                         String traceId, List<FieldErrorDetail> fieldErrors) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ErrorResponse.of(status.value(), code, message, path, traceId, fieldErrors));
    }

    private static ResponseEntity<Object> problemEntity(HttpStatus status, HttpHeaders headers, ErrorResponse body) {
        HttpHeaders writableHeaders = new HttpHeaders();
        writableHeaders.putAll(headers);
        writableHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return ResponseEntity.status(status).headers(writableHeaders).body(body);
    }

    private static Object rejectedValueOf(FieldError error) {
        String simpleFieldName = error.getField();
        int lastDot = simpleFieldName.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleFieldName = simpleFieldName.substring(lastDot + 1);
        }
        return SENSITIVE_FIELD_NAMES.contains(simpleFieldName.toLowerCase(Locale.ROOT))
                ? null
                : error.getRejectedValue();
    }

    private static String path(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return "";
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}

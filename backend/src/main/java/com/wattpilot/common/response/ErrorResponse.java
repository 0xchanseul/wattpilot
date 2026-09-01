package com.wattpilot.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Standard error payload for every non-2xx REST response.
 * Matches the {@code ErrorResponse} schema in docs/openapi.yaml and is served as
 * {@code application/problem+json}.
 *
 * <p>{@code status} carries the HTTP status code, while {@code code} carries the
 * application-level {@link com.wattpilot.common.exception.ErrorCode} name.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldErrorDetail> fieldErrors
) {

    public static ErrorResponse of(int status, String code, String message, String path, String traceId,
                                   List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(OffsetDateTime.now(ZoneOffset.UTC), status, code, message, path, traceId, fieldErrors);
    }
}

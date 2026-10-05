package com.wheretopark.controller.exception;

import lombok.Getter;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * JSON representation of an error that can be returned by a controller.
 *
 * <p>
 * Beyond the {@code status}/{@code message} pair, it carries the {@code timestamp} and the failing {@code path}, so a consumer can correlate an error with its own logs.
 */
@Getter
public class ErrorResponse {

    private final String status;
    private final String message;
    private final Instant timestamp;
    private final String path;

    /**
     * Creates an ErrorResponse with the given HTTP status and message. The status is serialized as its name only (e.g., "NOT_FOUND", "BAD_REQUEST").
     *
     * @param httpStatus the HTTP status
     * @param message the error message
     */
    public ErrorResponse(HttpStatus httpStatus, String message) {
        this(httpStatus, message, null);
    }

    /**
     * Creates an ErrorResponse with the failing request path.
     *
     * @param httpStatus the HTTP status
     * @param message the error message
     * @param path the failing request path, or {@code null} when unknown
     */
    public ErrorResponse(HttpStatus httpStatus, String message, String path) {
        this.status = httpStatus.name();
        this.message = message;
        this.timestamp = Instant.now();
        this.path = path;
    }
}

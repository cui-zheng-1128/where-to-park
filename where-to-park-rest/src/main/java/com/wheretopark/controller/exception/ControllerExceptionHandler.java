package com.wheretopark.controller.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Exception handler for the parking controllers.
 *
 * <p>
 * Status code contract: 200 / 304 / 400 / 405 / 429 / 500.
 *
 * <p>
 * Each handler maps one failure class to its status; {@code request} provides the failing path echoed in the {@link ErrorResponse}:
 * <ul>
 * <li>bean validation on a query parameter violated ({@code lat=146.5}) &rarr; 400</li>
 * <li>required query parameter missing (no {@code lat}) &rarr; 400</li>
 * <li>parameter not parseable ({@code lat=abc}, {@code ranking=FOO}) &rarr; 400</li>
 * <li>{@link IllegalArgumentException} (e.g. unknown parking id) &rarr; 400</li>
 * <li>non-GET method on this read-only API &rarr; 405, with an {@code Allow} header (RFC 9110)</li>
 * <li>anything unexpected &rarr; 500, without leaking internal detail</li>
 * </ul>
 */
@ControllerAdvice
public class ControllerExceptionHandler {

    /**
     * Maps a bean-validation violation on a query parameter to {@code 400}.
     *
     * @param exception the violation
     * @param request the failing request (for its path)
     * @return the error body
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        String message = exception.getConstraintViolations()
            .stream()
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    /**
     * Maps a missing required query parameter to {@code 400}.
     *
     * @param exception the failure
     * @param request the failing request (for its path)
     * @return the error body
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ErrorResponse handleMissingParameter(MissingServletRequestParameterException exception, HttpServletRequest request) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST,
            exception.getParameterName() + ": parameter is required", request.getRequestURI());
    }

    /**
     * Maps an unparseable query parameter to {@code 400}.
     *
     * @param exception the failure
     * @param request the failing request (for its path)
     * @return the error body
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST,
            exception.getName() + ": invalid value: " + exception.getValue(), request.getRequestURI());
    }

    /**
     * Maps an {@link IllegalArgumentException} (e.g. unknown parking id) to {@code 400}.
     *
     * @param exception the failure
     * @param request the failing request (for its path)
     * @return the error body
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    /**
     * Maps a non-GET method on this read-only API to {@code 405}, with an {@code Allow} header (RFC 9110).
     *
     * @param exception the failure
     * @param request the failing request (for its path)
     * @return the error response
     */
    @ResponseBody
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .allow(org.springframework.http.HttpMethod.GET)
            .body(new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED,
                exception.getMethod() + " is not supported; use GET", request.getRequestURI()));
    }

    /**
     * Maps anything unexpected to {@code 500}, without leaking internal detail.
     *
     * @param exception the failure
     * @param request the failing request (for its path)
     * @return the error body
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handleUnexpected(Exception exception, HttpServletRequest request) {
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "internal error", request.getRequestURI());
    }
}

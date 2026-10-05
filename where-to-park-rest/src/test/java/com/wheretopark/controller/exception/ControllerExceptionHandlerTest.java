package com.wheretopark.controller.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link ControllerExceptionHandler}.
 */
class ControllerExceptionHandlerTest {

    private final ControllerExceptionHandler handler = new ControllerExceptionHandler();

    @Test
    void shouldMapConstraintViolationToBadRequestWithDetails() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("nearby.lat");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be between -90 and 90");

        ErrorResponse response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)), request());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.name());
        assertThat(response.getMessage()).isEqualTo("nearby.lat must be between -90 and 90");
    }

    @Test
    void shouldMapMissingParameterToBadRequest() {
        ErrorResponse response = handler.handleMissingParameter(
            new MissingServletRequestParameterException("lat", "double"),
            request());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.name());
        assertThat(response.getMessage()).isEqualTo("lat: parameter is required");
    }

    @Test
    void shouldMapTypeMismatchToBadRequest() {
        ErrorResponse response = handler.handleTypeMismatch(
            new MethodArgumentTypeMismatchException("abc", double.class, "lat", null, null),
            request());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.name());
        assertThat(response.getMessage()).isEqualTo("lat: invalid value: abc");
    }

    @Test
    void shouldMapIllegalArgumentToBadRequest() {
        ErrorResponse response = handler.handleIllegalArgument(new IllegalArgumentException("lat out of range"), request());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.name());
        assertThat(response.getMessage()).isEqualTo("lat out of range");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/parkings/nearby");
        request.setRequestURI("/v1/parkings/nearby");
        return request;
    }
}

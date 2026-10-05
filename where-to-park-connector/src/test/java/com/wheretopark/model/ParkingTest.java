package com.wheretopark.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of {@link Parking}.
 */
class ParkingTest {

    private static final Location LOCATION = new Location(46.58, 0.34);
    private static final Instant NOW = Instant.parse("2026-09-28T18:37:33Z");

    @Test
    void shouldAcceptACompleteParking() {
        assertThatCode(() -> new Parking("poitiers:1", "poitiers", "NOTRE DAME", LOCATION, 146, 53, ParkingStatus.OPEN, NOW, NOW))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowNullableOptionalFields() {
        assertThatCode(() -> new Parking("poitiers:1", "poitiers", "NOTRE DAME", LOCATION, null, null, ParkingStatus.UNKNOWN, NOW, NOW))
            .doesNotThrowAnyException();
    }

    /**
     * Each mandatory field, nulled in turn, must be rejected with its own name in the message.
     *
     * @param field the field to null
     */
    @ParameterizedTest(name = "rejects null {0}")
    @ValueSource(strings = {"id", "cityId", "name", "location", "status", "sourceUpdatedAt", "fetchedAt"})
    void shouldRejectNullMandatoryField(String field) {
        ParkingBuilder builder = new ParkingBuilder();
        builder.nullField(field);

        assertThatThrownBy(builder::build)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(field);
    }

    /** A complete Parking whose fields can be null individually. */
    private static final class ParkingBuilder {
        private String id = "poitiers:1";
        private String cityId = "poitiers";
        private String name = "N";
        private Location location = LOCATION;
        private ParkingStatus status = ParkingStatus.OPEN;
        private Instant sourceUpdatedAt = NOW;
        private Instant fetchedAt = NOW;

        void nullField(String field) {
            switch (field) {
                case "id" -> id = null;
                case "cityId" -> cityId = null;
                case "name" -> name = null;
                case "location" -> location = null;
                case "status" -> status = null;
                case "sourceUpdatedAt" -> sourceUpdatedAt = null;
                case "fetchedAt" -> fetchedAt = null;
                default -> throw new IllegalArgumentException(field);
            }
        }

        Parking build() {
            return new Parking(id, cityId, name, location, 1, 1, status, sourceUpdatedAt, fetchedAt);
        }
    }
}

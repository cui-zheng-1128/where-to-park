package com.wheretopark.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of {@link Location}.
 */
class LocationTest {

    @Test
    void shouldAcceptBoundaryCoordinates() {
        assertThat(new Location(90, 180).lat()).isEqualTo(90);
        assertThat(new Location(-90, -180).lon()).isEqualTo(-180);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.0001, 90.0001})
    void shouldRejectOutOfRangeLatitude(double lat) {
        assertThatThrownBy(() -> new Location(lat, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lat out of range");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.0001, 180.0001})
    void shouldRejectOutOfRangeLongitude(double lon) {
        assertThatThrownBy(() -> new Location(0, lon))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lon out of range");
    }
}

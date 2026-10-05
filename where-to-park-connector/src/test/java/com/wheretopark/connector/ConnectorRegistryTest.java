package com.wheretopark.connector;

import com.wheretopark.model.Parking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of {@link ConnectorRegistry}.
 */
class ConnectorRegistryTest {

    @Test
    void shouldExposeTheGivenConnectors() {
        ParkingSourceConnector connector = stub("poitiers");

        ConnectorRegistry registry = new ConnectorRegistry(List.of(connector));

        assertThat(registry.connectors()).containsExactly(connector);
    }

    @Test
    void shouldFailFastOnANullList() {
        assertThatThrownBy(() -> new ConnectorRegistry(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldFreezeTheConnectorList() {
        ConnectorRegistry registry = new ConnectorRegistry(List.of(stub("poitiers")));

        assertThatThrownBy(() -> registry.connectors().add(stub("nantes")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /** A connector stub: the record component provides {@code cityId()}, the rest is fixed. */
    private static ParkingSourceConnector stub(String cityId) {
        record Stub(String cityId) implements ParkingSourceConnector {
            @Override
            public Duration pollInterval() {
                return Duration.ofSeconds(60);
            }

            @Override
            public List<Parking> fetch() {
                return List.of();
            }
        }

        return new Stub(cityId);
    }
}

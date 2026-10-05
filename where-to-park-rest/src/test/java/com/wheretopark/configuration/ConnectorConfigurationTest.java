package com.wheretopark.configuration;

import com.wheretopark.connector.ConnectorRegistry;
import com.wheretopark.connector.ParkingSourceConnector;
import com.wheretopark.connector.datafair.DataFairConnector;
import com.wheretopark.connector.opendatasoft.OpendatasoftConnector;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of {@link ConnectorConfiguration}.
 */
class ConnectorConfigurationTest {

    @Test
    void shouldBuildOneConnectorPerEnabledSource() {
        ParkingIngestionProperties properties = new ParkingIngestionProperties();
        properties.setSources(Map.of(
            "poitiers",
            source("data-fair", true, null),
            "nantes",
            source("opendatasoft", true, fieldMapping()),
            "disabled-city",
            source("data-fair", false, null)));

        ConnectorRegistry registry = new ConnectorConfiguration(properties).connectorRegistry();

        assertThat(registry.connectors()).hasSize(2);
        assertThat(registry.connectors()).extracting(ParkingSourceConnector::cityId)
            .containsExactlyInAnyOrder("poitiers", "nantes");
    }

    @Test
    void shouldInstantiateTheRightConnectorTypePerSourceType() {
        ParkingIngestionProperties properties = new ParkingIngestionProperties();
        properties.setSources(Map.of(
            "poitiers",
            source("data-fair", true, null),
            "nantes",
            source("opendatasoft", true, fieldMapping())));

        ConnectorRegistry registry = new ConnectorConfiguration(properties).connectorRegistry();

        assertThat(registry.connectors())
            .anySatisfy(c -> assertThat(c).isInstanceOf(DataFairConnector.class))
            .anySatisfy(c -> assertThat(c).isInstanceOf(OpendatasoftConnector.class));
    }

    @Test
    void shouldDefaultThePollIntervalTo60SecondsWhenUnset() {
        ParkingIngestionProperties.Source source = source("data-fair", true, null);

        assertThat(source.pollIntervalOrDefault()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldRejectAnUnknownSourceType() {
        ParkingIngestionProperties properties = new ParkingIngestionProperties();
        properties.setSources(Map.of("x", source("arcgis", true, null)));

        assertThatThrownBy(() -> new ConnectorConfiguration(properties).connectorRegistry())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown source type 'arcgis'");
    }

    private static ParkingIngestionProperties.Source source(String type,
            boolean enabled,
            ParkingIngestionProperties.FieldMappingProperties fieldMapping) {
        ParkingIngestionProperties.Source source = new ParkingIngestionProperties.Source();
        source.setEnabled(enabled);
        source.setType(type);
        source.setBaseUrl("https://example.fr");
        source.setDataset("dataset");
        source.setFieldMapping(fieldMapping);
        return source;
    }

    private static ParkingIngestionProperties.FieldMappingProperties fieldMapping() {
        ParkingIngestionProperties.FieldMappingProperties mapping = new ParkingIngestionProperties.FieldMappingProperties();
        mapping.setIdField("id");
        mapping.setNameField("nom");
        mapping.setLatField("geo.lat");
        mapping.setLonField("geo.lon");
        return mapping;
    }
}

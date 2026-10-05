package com.wheretopark.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.wheretopark.connector.ConnectorRegistry;
import com.wheretopark.connector.ParkingSourceConnector;
import com.wheretopark.connector.datafair.DataFairConnector;
import com.wheretopark.connector.opendatasoft.OpendatasoftConnector;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * Wires the plain-Java connectors of the connector module as Spring beans, from configuration.
 * Connectors stay framework-free so they can be unit-tested and reused without Spring on the classpath; all Spring coupling lives here.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(ParkingIngestionProperties.class)
public class ConnectorConfiguration {

    private final ParkingIngestionProperties properties;

    /**
     * Builds the registry of active connectors from configuration.
     *
     * @return the connector registry
     */
    @Bean
    public ConnectorRegistry connectorRegistry() {
        List<ParkingSourceConnector> connectors = properties.getSources()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().isEnabled())
            .map(entry -> connector(entry.getKey(), entry.getValue()))
            .toList();
        log.info("Registered {} connector(s): {}", connectors.size(), connectors.stream().map(ParkingSourceConnector::cityId).toList());
        return new ConnectorRegistry(connectors);
    }

    /**
     * Instantiates one connector from its configuration entry.
     *
     * @param cityId the city identifier (map key)
     * @param source the source configuration
     * @return the connector instance
     */
    private static ParkingSourceConnector connector(String cityId, ParkingIngestionProperties.Source source) {
        return switch (source.getType()) {
            case "data-fair" -> source.getEndpoint() != null
                ? new DataFairConnector(cityId, java.net.URI.create(source.getEndpoint()), source.pollIntervalOrDefault())
                : new DataFairConnector(cityId, source.getBaseUrl(), source.getDataset(), source.pollIntervalOrDefault());
            case "opendatasoft" -> {
                ParkingIngestionProperties.FieldMappingProperties fm = source.getFieldMapping();
                OpendatasoftConnector.FieldMapping mapping = new OpendatasoftConnector.FieldMapping(fm.getIdField(), fm.getNameField(), fm.getLatField(), fm.getLonField(), fm.getCapacityField(),
                    fm.getAvailableSpotsField(), fm.getStatusField(), fm.getTimestampField(), fm.getStatusMapping());
                yield source.getEndpoint() != null
                    ? new OpendatasoftConnector(cityId, java.net.URI.create(source.getEndpoint()), source.pollIntervalOrDefault(), mapping)
                    : new OpendatasoftConnector(cityId, source.getBaseUrl(), source.getDataset(), source.pollIntervalOrDefault(), mapping);
            }
            default -> throw new IllegalStateException("Unknown source type '" + source.getType() + "' for city '" + cityId + "'");
        };
    }
}

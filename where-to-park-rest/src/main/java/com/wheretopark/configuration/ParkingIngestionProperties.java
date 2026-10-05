package com.wheretopark.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration-driven source registry: adding a city is a YAML entry plus (for a new platform type) one connector class â€” no change to the ingestion or query code.
 *
 * <pre>
 * parking:
 *   sources:
 *     poitiers:
 *       enabled: true
 *       type: data-fair
 *       base-url: https://data.grandpoitiers.fr
 *       dataset: mobilites-stationnement-des-parkings-en-temps-reel
 *       poll-interval: PT60S
 *       attribution:                      # mandatory (@NotNull)
 *         source: Grand Poitiers - open data
 *         license: Open License 2.0
 *         url: https://data.grandpoitiers.fr/datasets/mobilites-stationnement-des-parkings-en-temps-reel
 * </pre>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "parking")
public class ParkingIngestionProperties {

    /**
     * City sources keyed by cityId. Empty map when no source is configured.
     */
    @NotNull
    private Map<String, @Valid Source> sources = new HashMap<>();

    /**
     * One city source.
     */
    @Data
    @Validated
    public static class Source {

        /**
         * Whether this source is active.
         */
        private boolean enabled;

        /**
         * Source platform type (e.g. {@code data-fair}, {@code opendatasoft}).
         */
        @NotBlank
        private String type;

        /**
         * Base URL of the open-data portal.
         */
        @NotBlank
        private String baseUrl;

        /**
         * Dataset identifier on the portal.
         */
        @NotBlank
        private String dataset;

        /**
         * Full records-endpoint URL overriding the platform default. Null means the connector builds the canonical endpoint from {@code base-url} + {@code dataset}.
         * Use it for non-standard deployments (legacy path, proxy rewrite).
         */
        private String endpoint;

        /**
         * Minimum delay between two polls. Defaults to 60 s when omitted.
         */
        private Duration pollInterval;

        /**
         * Data-source attribution (see {@code AttributionDTO}; legally mandatory).
         */
        @NotNull
        @Valid
        private Attribution attribution;

        /**
         * Field mapping for Opendatasoft connectors. Null for Data Fair connectors.
         */
        @Valid
        private FieldMappingProperties fieldMapping;

        /**
         * Returns the configured poll interval, or the 60 s default when unset.
         *
         * @return the effective poll interval
         */
        public Duration pollIntervalOrDefault() {
            return pollInterval == null ? Duration.ofSeconds(60) : pollInterval;
        }
    }

    /**
     * Data-source attribution of one city (see {@code AttributionDTO}).
     */
    @Data
    @Validated
    public static class Attribution {

        @NotBlank
        private String source;

        @NotBlank
        private String license;

        @NotBlank
        private String url;
    }

    /**
     * Spring Boot bindable form of the Opendatasoft field mapping.
     * Converted to {@code OpendatasoftConnector.FieldMapping} in {@link ConnectorConfiguration}.
     */
    @Data
    @Validated
    public static class FieldMappingProperties {

        @NotBlank
        private String idField;

        @NotBlank
        private String nameField;

        @NotBlank
        private String latField;

        @NotBlank
        private String lonField;

        private String capacityField;
        private String availableSpotsField;
        private String statusField;
        private String timestampField;

        // Codeâ†’status table for the status field (optional; null = every record reports UNKNOWN).
        private Map<Integer, String> statusMapping;
    }
}

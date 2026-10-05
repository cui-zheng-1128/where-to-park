package com.wheretopark.connector.opendatasoft;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import com.wheretopark.connector.AbstractHttpSourceConnector;
import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Generic connector for the Opendatasoft platform.
 *
 * <p>
 * Endpoint: {@code {baseUrl}/api/explore/v2.1/catalog/datasets/{dataset}/records} (paged).
 *
 * <p>
 * Field mapping is configuration-driven per city because each city names its columns differently (French with or without accents, camelCase, snake_case...).
 * The mapping is expressed as field paths relative to a record, e.g. {@code "grp_nom"} or {@code "location.lat"}.
 *
 * <p>
 * The connector never guesses a status convention: without a declared {@code statusMapping}, or with no status field at all (static datasets), every record reports {@code UNKNOWN}.
 */
@Slf4j
public class OpendatasoftConnector extends AbstractHttpSourceConnector {

    private final FieldMapping mapping;

    /**
     * Creates a connector for a standard Opendatasoft deployment, whose records endpoint is the canonical {@code {baseUrl}/api/explore/v2.1/catalog/datasets/{dataset}/records}.
     *
     * @param cityId the city identifier
     * @param baseUrl the portal base URL (no trailing slash)
     * @param dataset the dataset identifier
     * @param pollInterval the minimum delay between two polls
     * @param mapping the field mapping configuration
     */
    public OpendatasoftConnector(String cityId, String baseUrl, String dataset, Duration pollInterval, FieldMapping mapping) {
        super(cityId, URI.create(baseUrl + "/api/explore/v2.1/catalog/datasets/" + dataset + "/records"), pollInterval);
        this.mapping = mapping;
    }

    /**
     * Creates a connector for a non-standard deployment whose records endpoint differs from the canonical one (legacy path, proxy rewrite, ...).
     *
     * @param cityId the city identifier
     * @param endpoint the full records-endpoint URL, without paging parameters
     * @param pollInterval the minimum delay between two polls
     * @param mapping the field mapping configuration
     */
    public OpendatasoftConnector(String cityId, URI endpoint, Duration pollInterval, FieldMapping mapping) {
        super(cityId, endpoint, pollInterval);
        this.mapping = mapping;
    }

    /**
     * Maps one record of the {@code results} array to the canonical model.
     *
     * @param record one element of {@code results}
     * @return the parking, or {@code null} when the record is unusable (dropped with a warning)
     */
    @Override
    protected Parking toParking(JsonNode record) {
        String sourceId = textAt(record, mapping.idField());
        String name = textAt(record, mapping.nameField());

        if (sourceId == null || name == null) {
            log.warn("[{}] dropping record missing id/name", cityId());
            return null;
        }

        Location location = resolveLocation(record, mapping);
        if (location == null) {
            log.warn("[{}] dropping '{}': no usable coordinates", cityId(), name);
            return null;
        }

        Integer capacity = intAt(record, mapping.capacityField());
        Integer availableSpots = intAt(record, mapping.availableSpotsField());
        ParkingStatus status = resolveStatus(record, availableSpots, mapping, cityId());
        Instant sourceUpdatedAt = resolveTimestamp(record, mapping, cityId());

        if (sourceUpdatedAt == null) {
            // Static datasets without a timestamp: use the fetch time as a fallback. The freshness semantic is preserved by the poll interval.
            sourceUpdatedAt = Instant.now();
        }

        // fetchedAt is stamped by the base class with the actual fetch time.
        return new Parking(cityId() + ":" + sourceId, cityId(), name, location, capacity, availableSpots, status, sourceUpdatedAt, Instant.EPOCH);
    }

    /**
     * Resolves the configured lat/lon field pair into a {@link Location}.
     *
     * @param record the source record
     * @param mapping the field mapping
     * @return the location, or {@code null} when missing or non-numeric
     */
    private static Location resolveLocation(JsonNode record, FieldMapping mapping) {
        return locationFrom(textAt(record, mapping.latField()), textAt(record, mapping.lonField()));
    }

    /**
     * Resolves the parking status from the configured status field and code table.
     *
     * @param record the source record
     * @param availableSpots the already-resolved available spots (used by occupancy-dependent codes)
     * @param mapping the field mapping
     * @param cityId the city identifier (for logging)
     * @return the status, or {@code UNKNOWN} when no status field / code / mapping is available
     */
    private static ParkingStatus resolveStatus(JsonNode record, Integer availableSpots, FieldMapping mapping, String cityId) {
        if (mapping.statusField() == null) {
            // Static dataset: no real-time status available.
            return ParkingStatus.UNKNOWN;
        }

        Integer statusCode = intAt(record, mapping.statusField());
        if (statusCode == null) {
            return ParkingStatus.UNKNOWN;
        }

        // No declared codeâ†’status table: the source convention is unknown, never guess it.
        if (mapping.statusMapping() == null) {
            return ParkingStatus.UNKNOWN;
        }
        return resolveConfigured(statusCode, availableSpots, mapping.statusMapping(), cityId);
    }

    /**
     * Resolves the source-update timestamp from the configured field, accepting both plain ISO-8601 instants and offset-bearing variants.
     *
     * @param record the source record
     * @param mapping the field mapping
     * @param cityId the city identifier (for logging)
     * @return the timestamp, or {@code null} when absent or unparseable
     */
    private static Instant resolveTimestamp(JsonNode record, FieldMapping mapping, String cityId) {
        String text = textAt(record, mapping.timestampField());
        if (text == null) {
            return null;
        }

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            // ISO-8601 with offset (e.g. "2026-10-01T14:38:41+00:00") is not accepted by Instant.parse
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException e2) {
                log.debug("[{}] unparseable timestamp: {}", cityId, text);
                return null;
            }
        }
    }

    /**
     * Resolves a status code against a codeâ†’status table.
     *
     * @param statusCode the source status code
     * @param availableSpots the already-resolved available spots (used by {@code OPEN_OR_FULL})
     * @param table the codeâ†’status table
     * @param cityId the city identifier (for logging)
     * @return the mapped status, or {@code UNKNOWN} for unknown codes and misconfigured values
     */
    private static ParkingStatus resolveConfigured(int statusCode, Integer availableSpots, Map<Integer, String> table, String cityId) {
        String mapped = table.get(statusCode);
        if (mapped == null) {
            return ParkingStatus.UNKNOWN;
        }

        // Occupancy-dependent code: open only when the source reports free spots.
        if ("OPEN_OR_FULL".equals(mapped)) {
            return openOrFull(availableSpots);
        }

        try {
            return ParkingStatus.valueOf(mapped);
        } catch (IllegalArgumentException e) {
            log.warn("[{}] misconfigured statusMapping value '{}' for code {}", cityId, mapped, statusCode);
            return ParkingStatus.UNKNOWN;
        }
    }

    /**
     * Resolves an occupancy-dependent code: open only when the source reports free spots.
     *
     * @param availableSpots the available spots figure
     * @return {@code OPEN} when at least one spot is reported, {@code FULL} otherwise
     */
    private static ParkingStatus openOrFull(Integer availableSpots) {
        return availableSpots != null && availableSpots > 0 ? ParkingStatus.OPEN : ParkingStatus.FULL;
    }

    /**
     * Field mapping configuration for one Opendatasoft dataset. Paths are dot-separated, relative to a record object. Optional fields left {@code null} are simply absent from the resulting parking.
     *
     * @param idField unique parking identifier field (required)
     * @param nameField parking name field (required)
     * @param latField latitude field (required, e.g. {@code "location.lat"})
     * @param lonField longitude field (required)
     * @param capacityField total capacity field (optional)
     * @param availableSpotsField real-time available spots field (optional)
     * @param statusField real-time status code field (optional)
     * @param timestampField last-update timestamp field (optional)
     * @param statusMapping codeâ†’status table (optional): values are {@code OPEN|FULL|CLOSED|UNKNOWN}, or {@code OPEN_OR_FULL} for occupancy-dependent codes; null = every record reports UNKNOWN
     */
    public record FieldMapping(
            String idField,
            String nameField,
            String latField,
            String lonField,
            String capacityField,
            String availableSpotsField,
            String statusField,
            String timestampField,
            Map<Integer, String> statusMapping) {
    }
}

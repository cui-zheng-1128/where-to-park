package com.wheretopark.connector.datafair;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import com.wheretopark.connector.AbstractHttpSourceConnector;
import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * Generic connector for the Data Fair platform (Koumoul), used e.g. by Grand Poitiers.
 *
 * <p>
 * Endpoint: {@code {baseUrl}/data-fair/api/v1/datasets/{dataset}/lines} (paged).
 * Expected line fields (French names, accents included): {@code Id / Nom / Capacite / Places / taux_doccupation / _geopoint / Dernière_mise_à_jour_Base}.
 *
 * <p>
 * Normalization rules:
 * <ul>
 * <li>{@code _geopoint} is the string {@code "lat, lon"} (WGS84); lines without usable coordinates are dropped with a warning since they can never match a nearby query;</li>
 * <li>{@code Places} → {@code availableSpots}; {@code null} stays {@code null} ("unknown"), only an explicit 0 may mean "full";</li>
 * <li>status: {@code Places > 0} → OPEN; {@code Places == 0} with {@code taux_doccupation >= 100} → FULL; {@code Places == 0} without a confirming occupancy rate → UNKNOWN;</li>
 * <li>{@code Dernière_mise_à_jour_Base} → {@code sourceUpdatedAt}; lines without it are dropped (freshness is mandatory in the canonical model).</li>
 * </ul>
 *
 * <p>
 * Rate limits (anonymous): 600 req/min and 20 s of cumulative processing per minute — the configured poll interval must stay at or above the source refresh period.
 */
@Slf4j
public class DataFairConnector extends AbstractHttpSourceConnector {

    /**
     * Creates a connector for a standard Data Fair deployment, whose records endpoint is the canonical {@code {baseUrl}/data-fair/api/v1/datasets/{dataset}/lines}.
     *
     * @param cityId the city identifier
     * @param baseUrl the portal base URL (no trailing slash)
     * @param dataset the dataset identifier
     * @param pollInterval the minimum delay between two polls
     */
    public DataFairConnector(String cityId, String baseUrl, String dataset, Duration pollInterval) {
        super(cityId, URI.create(baseUrl + "/data-fair/api/v1/datasets/" + dataset + "/lines?format=json"), pollInterval);
    }

    /**
     * Creates a connector for a non-standard deployment whose records endpoint differs from the canonical one (legacy path, proxy rewrite, ...).
     *
     * @param cityId the city identifier
     * @param endpoint the full records-endpoint URL, without paging parameters
     * @param pollInterval the minimum delay between two polls
     */
    public DataFairConnector(String cityId, URI endpoint, Duration pollInterval) {
        super(cityId, endpoint, pollInterval);
    }

    /**
     * Maps one line of the {@code results} array to the canonical model.
     *
     * @param line one element of {@code results}
     * @return the parking, or {@code null} when the record is unusable (dropped with a warning)
     */
    @Override
    protected Parking toParking(JsonNode line) {
        String sourceId = textAt(line, "Id");
        String name = textAt(line, "Nom");
        String timestamp = textAt(line, "Dernière_mise_à_jour_Base");

        if (sourceId == null || name == null || timestamp == null) {
            log.warn("[{}] dropping record missing Id/Nom/timestamp", cityId());
            return null;
        }

        Location location = resolveLocation(line);
        if (location == null) {
            log.warn("[{}] dropping '{}': no usable coordinates", cityId(), name);
            return null;
        }

        Integer capacity = intAt(line, "Capacite");
        Integer places = intAt(line, "Places");
        ParkingStatus status = resolveStatus(places, doubleAt(line, "taux_doccupation"));

        // fetchedAt is stamped by the base class with the actual fetch time.
        return new Parking(cityId() + ":" + sourceId, cityId(), name, location, capacity, places, status, Instant.parse(timestamp), Instant.EPOCH);
    }

    /**
     * Resolves the parking status from the spots figure, confirmed by the occupancy rate.
     *
     * @param places the available spots ({@code Places})
     * @param occupancyRate the occupancy percentage ({@code taux_doccupation})
     * @return OPEN with free spots, FULL at zero spots with a confirming rate, UNKNOWN otherwise
     */
    private static ParkingStatus resolveStatus(Integer places, Double occupancyRate) {
        if (places == null) {
            return ParkingStatus.UNKNOWN;
        }

        if (places > 0) {
            return ParkingStatus.OPEN;
        }

        // places == 0: "full" only when the occupancy rate confirms it; otherwise the source cannot tell a full parking from a closed one.
        return occupancyRate != null && occupancyRate >= 100.0 ? ParkingStatus.FULL : ParkingStatus.UNKNOWN;
    }

    /**
     * Resolves the {@code _geopoint} field ({@code "lat, lon"}) into a {@link Location}.
     *
     * @param line the source line
     * @return the location, or {@code null} when missing, malformed or non-numeric
     */
    private static Location resolveLocation(JsonNode line) {
        String geopoint = textAt(line, "_geopoint");
        if (geopoint == null || geopoint.isBlank()) {
            return null;
        }

        // Expected shape is exactly "lat, lon": anything else is unusable.
        String[] parts = geopoint.split(",");
        if (parts.length != 2) {
            return null;
        }

        return locationFrom(parts[0], parts[1]);
    }
}

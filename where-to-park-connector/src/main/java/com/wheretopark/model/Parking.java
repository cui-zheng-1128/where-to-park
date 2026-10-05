package com.wheretopark.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * Canonical parking model shared by every city.
 *
 * <p>
 * Source-specific field names, units or coordinate reference systems must never reach this type (anti-corruption boundary): connectors normalize everything into this model.
 */
public record Parking(
        String id,
        String cityId,
        String name,
        Location location,
        Integer capacity,
        Integer availableSpots,
        ParkingStatus status,
        Instant sourceUpdatedAt,
        Instant fetchedAt) {

    /**
     * Creates a parking, rejecting null for every mandatory field.
     *
     * @throws NullPointerException when any of id, cityId, name, location, status, sourceUpdatedAt or fetchedAt is null
     */
    public Parking {
        requireNonNull(id, "id is required");
        requireNonNull(cityId, "cityId is required");
        requireNonNull(name, "name is required");
        requireNonNull(location, "location is required");
        requireNonNull(status, "status is required");
        requireNonNull(sourceUpdatedAt, "sourceUpdatedAt is required");
        requireNonNull(fetchedAt, "fetchedAt is required");
    }
}

package com.wheretopark.connector;

import com.wheretopark.model.Parking;

import java.util.List;

/**
 * Write port of the parking store, used by the ingestion layer only.
 *
 * <p>
 * Defined here (and not in the runtime module) because "city snapshot" is an ingestion concept; the runtime module provides the implementation.
 * Implementations must guarantee that {@link #replaceAll} is atomic from the readers' perspective: a reader never observes a half-replaced city snapshot.
 */
public interface ParkingStore {

    /**
     * Atomically replaces the whole snapshot of one city.
     *
     * @param cityId the city whose data is replaced
     * @param parkings the new snapshot (already normalized, validated and stamped with {@code fetchedAt} by the connector â€” staleness tracking rides on it)
     */
    void replaceAll(String cityId, List<Parking> parkings);
}

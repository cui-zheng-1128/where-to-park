package com.wheretopark.service;

import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.service.model.Ranking;
import com.wheretopark.service.model.SearchResult;

/**
 * Business logic of the nearby parking search.
 */
public interface ParkingService {

    /**
     * Finds the parking around an origin, within a radius, sorted with the requested strategy.
     *
     * @param origin the searched position (WGS84)
     * @param radiusMeters the search radius in meters
     * @param limit the maximum number of results
     * @param requested the requested ranking strategy; {@link Ranking#DRIVE_TIME} degrades to {@link Ranking#DISTANCE} until a routing engine exists
     * @return the matching parking and the ranking actually applied
     */
    SearchResult findNearby(Location origin, int radiusMeters, int limit, Ranking requested);

    /**
     * Returns one parking by its identifier.
     *
     * @param id the parking identifier (e.g. {@code poitiers:1})
     * @return the matching parking
     * @throws IllegalArgumentException when no parking exists with this id
     */
    Parking getById(String id);
}

package com.wheretopark.service.model;

/**
 * Sort strategies for the nearby search.
 */
public enum Ranking {

    // 2D great-circle distance ascending - the mode-neutral default.
    DISTANCE,

    // Largest availableSpots first, distance as tie-break.
    AVAILABILITY,

    /**
     * Drive-time re-ranking (shortest drive first), intended for car drivers.
     *
     * <p>
     * May degrade to {@link #DISTANCE} in either of these cases:
     * <ul>
     * <li>no routing engine (OSRM / Valhalla / commercial traffic API) is plugged in, drive time cannot be computed;</li>
     * <li>routing engine exists, whenever it is down, over quota, or times out for a given request: that single request falls back to {@link #DISTANCE} instead of failing.
     * </ul>
     */
    DRIVE_TIME
}

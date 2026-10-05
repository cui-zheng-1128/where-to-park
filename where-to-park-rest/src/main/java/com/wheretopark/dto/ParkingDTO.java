package com.wheretopark.dto;

import lombok.Value;

import com.wheretopark.model.Location;
import com.wheretopark.model.ParkingStatus;

import java.time.Instant;

/**
 * v1 JSON representation of a parking returned by the nearby search.
 *
 * <p>
 * Contract: distances in meters, WGS84 coordinates, ISO-8601 UTC timestamps.
 */
@Value
public class ParkingDTO {

    String id;
    String name;
    Location location;
    int distanceMeters;
    Integer capacity;

    // availableSpots keeps null when the source provides no figure, 0 when the parking is full.
    Integer availableSpots;

    // Drive time in seconds; null until a routing engine exists.
    Integer driveSeconds;

    ParkingStatus status;
    Instant sourceUpdatedAt;

    // When the backend pulled this record from the source (staleness signal for clients).
    Instant fetchedAt;
    String cityId;
}

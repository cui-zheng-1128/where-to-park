package com.wheretopark.service.model;

import com.wheretopark.model.Parking;

import lombok.Value;

/**
 * A {@link Parking} together with its 2D distance (in meters) from the searched origin.
 */
@Value
public class ScoredParking {

    Parking parking;
    int distanceMeters;
}

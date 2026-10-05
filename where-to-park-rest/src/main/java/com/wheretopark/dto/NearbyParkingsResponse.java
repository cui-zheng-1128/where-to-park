package com.wheretopark.dto;

import lombok.Value;

import com.wheretopark.service.model.Ranking;

import java.util.List;

/**
 * Envelope of the nearby search response.
 * An object (not a bare array) so that fields can only ever be ADDED, never removed or changed - every existing frontend keeps working forever.
 *
 * <p>
 * {@code rankingRequested} vs {@code rankingApplied} is exposed so the response never lies about the sort when {@code DRIVE_TIME} degrades to {@code DISTANCE}.
 */
@Value
public class NearbyParkingsResponse {

    Ranking rankingRequested;
    Ranking rankingApplied;
    List<ParkingDTO> results;
    List<AttributionDTO> attribution;
}

package com.wheretopark.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.repository.ParkingRepository;
import com.wheretopark.service.ParkingService;
import com.wheretopark.service.model.Ranking;
import com.wheretopark.service.model.ScoredParking;
import com.wheretopark.service.model.SearchResult;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Default implementation of {@link ParkingService}, backed by the parking store.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingServiceImpl implements ParkingService {

    // Earth mean radius in meters (used by the haversine formula).
    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private final ParkingRepository parkingRepository;

    /**
     * Returns one parking by its identifier.
     *
     * @param id the parking identifier (e.g. {@code poitiers:1})
     * @return the matching parking
     * @throws IllegalArgumentException when no parking exists with this id
     */
    @Override
    public Parking getById(String id) {
        return parkingRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("unknown parking: " + id));
    }

    /**
     * Finds the parking around an origin, within a radius, sorted with the requested strategy. {@code DRIVE_TIME} degrades to {@code DISTANCE} until a routing engine exists.
     *
     * @param origin the searched position (WGS84)
     * @param radiusMeters the search radius in meters
     * @param limit the maximum number of results
     * @param requested the requested ranking strategy
     * @return the matching parking and the ranking actually applied
     */
    @Override
    public SearchResult findNearby(@NonNull Location origin, int radiusMeters, int limit, @NonNull Ranking requested) {
        Ranking applied = requested == Ranking.DRIVE_TIME ? Ranking.DISTANCE : requested;

        if (applied != requested) {
            log.debug("ranking {} requested but not supported yet, degrading to {}", requested, applied);
        }

        List<ScoredParking> results = parkingRepository.findAll()
            .stream()
            .map(parking -> new ScoredParking(parking, distanceMeters(origin, parking.location())))
            .filter(scored -> scored.getDistanceMeters() <= radiusMeters)
            .sorted(comparator(applied))
            .limit(limit)
            .toList();
        return new SearchResult(results, applied);
    }

    /**
     * Builds a comparator for {@link ScoredParking} according to the applied ranking strategy.
     *
     * @param applied the ranking strategy actually applied
     * @return the comparator
     */
    private static Comparator<ScoredParking> comparator(Ranking applied) {
        Comparator<ScoredParking> byDistance = Comparator.comparingInt(ScoredParking::getDistanceMeters);
        // Most available spots first (reversed); unknown (null) sorts last via -1.
        Comparator<ScoredParking> byAvailability = Comparator.comparingInt(ParkingServiceImpl::availabilityOrUnknown).reversed();
        Comparator<ScoredParking> byId = Comparator.comparing(scored -> scored.getParking().id());

        return switch (applied) {
            case DISTANCE -> byDistance.thenComparing(byAvailability).thenComparing(byId);
            case AVAILABILITY -> byAvailability.thenComparing(byDistance).thenComparing(byId);
            // Unreachable: DRIVE_TIME is degraded to DISTANCE before this point.
            case DRIVE_TIME -> throw new IllegalStateException("DRIVE_TIME must be degraded to DISTANCE before sorting");
        };
    }

    /**
     * Maps {@code availableSpots} to a sortable key: unknown ({@code null}) becomes {@code -1} so that, after the comparator is reversed, a parking with no figure ranks below any known one.
     *
     * @param scored the scored parking
     * @return the sortable key
     */
    private static int availabilityOrUnknown(ScoredParking scored) {
        Integer availableSpots = scored.getParking().availableSpots();
        return availableSpots == null ? -1 : availableSpots;
    }

    /**
     * Great-circle distance in meters between two WGS84 points (haversine formula), 2D only. Accurate to ~0.5% on a sphere, sufficient for the {@code <= 5000 m} radii allowed by the API.
     *
     * @param a the first point
     * @param b the second point
     * @return the distance in meters
     */
    private static int distanceMeters(Location a, Location b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        // Haversine: h = sinÂ²(Î”lat/2) + cos(lat1)Â·cos(lat2)Â·sinÂ²(Î”lon/2); d = 2RÂ·asin(âˆšh).
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (int) Math.round(2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h)));
    }
}

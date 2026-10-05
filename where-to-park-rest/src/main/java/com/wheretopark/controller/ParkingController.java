package com.wheretopark.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

import com.wheretopark.configuration.ParkingIngestionProperties;
import com.wheretopark.dto.AttributionDTO;
import com.wheretopark.dto.NearbyParkingsResponse;
import com.wheretopark.dto.ParkingDTO;
import com.wheretopark.mapper.ParkingMapper;
import com.wheretopark.model.Location;
import com.wheretopark.service.ParkingService;
import com.wheretopark.service.model.Ranking;
import com.wheretopark.service.model.ScoredParking;
import com.wheretopark.service.model.SearchResult;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Read-only controller of the parking resource.
 *
 * <p>
 * Only {@code GET} is mapped; any other method yields {@code 405 Method Not Allowed}.
 *
 * <p>
 * {@code GET /v1/parkings/nearby?lat=46.58&lon=0.34&radius=1000&limit=20&ranking=DISTANCE}
 * <br>
 * Units: distances in meters, WGS84 coordinates, ISO-8601 UTC timestamps.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/v1/parkings", produces = "application/json")
public class ParkingController {

    // Half the shortest source refresh period (real-time sources refresh every 60 s).
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic().staleWhileRevalidate(Duration.ofSeconds(30));

    private final ParkingService parkingService;
    private final ParkingMapper parkingMapper;
    private final ParkingIngestionProperties properties;

    /**
     * Finds the parking near a position, within a radius, sorted with the requested strategy.
     *
     * <p>
     * Honours {@code If-None-Match}: when the client already holds the current data version,
     * answers {@code 304 Not Modified} with no body (the response would otherwise be identical).
     *
     * @param lat the latitude of the origin (WGS84, between -90 and 90)
     * @param lon the longitude of the origin (WGS84, between -180 and 180)
     * @param radius the search radius in meters (between 50 and 5000)
     * @param limit the maximum number of results (between 1 and 100)
     * @param ranking the sort strategy ({@code DRIVE_TIME} degrades to {@code DISTANCE})
     * @param ifNoneMatch the client-held ETag, if any
     * @return the matching parkings, sorted, with their data-source attribution
     */
    @GetMapping("/nearby")
    public ResponseEntity<NearbyParkingsResponse> nearby(
            @RequestParam @NotNull @Min(value = -90, message = "must be between -90 and 90") @Max(value = 90, message = "must be between -90 and 90") double lat,
            @RequestParam @NotNull @Min(value = -180, message = "must be between -180 and 180") @Max(value = 180, message = "must be between -180 and 180") double lon,
            @RequestParam(defaultValue = "1000") @Min(value = 50, message = "must be between 50 and 5000") @Max(value = 5000, message = "must be between 50 and 5000") int radius,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "must be between 1 and 100") @Max(value = 100, message = "must be between 1 and 100") int limit,
            @RequestParam(defaultValue = "DISTANCE") Ranking ranking,
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {

        SearchResult result = parkingService.findNearby(new Location(lat, lon), radius, limit, ranking);
        List<ParkingDTO> results = result.getResults()
            .stream()
            .map(parkingMapper::toDTO)
            .toList();

        // If the client already holds the current data version, answer 304 without a body.
        String eTag = dataVersion(results);
        if (eTag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .cacheControl(CACHE)
                .eTag(eTag)
                .build();
        }

        NearbyParkingsResponse body = new NearbyParkingsResponse(ranking, result.getAppliedRanking(), results, attributionOf(results));
        return ResponseEntity.ok()
            .cacheControl(CACHE)
            .eTag(eTag)
            .body(body);
    }

    /**
     * Returns one parking by its identifier.
     *
     * @param id the parking identifier (e.g. {@code poitiers:1})
     * @return the parking, or {@code 400} via the exception handler when the id is unknown
     */
    @GetMapping("/{id}")
    public ParkingDTO byId(@PathVariable String id) {
        // distanceMeters is origin-dependent and meaningless here: 0 is the neutral value.
        return parkingMapper.toDTO(new ScoredParking(parkingService.getById(id), 0));
    }

    /**
     * Picks the attributions of the cities present in the response (see {@link AttributionDTO}), and never credits a city whose data is not part of the response.
     *
     * @param results the response parking DTOs
     * @return the matching attributions
     */
    private List<AttributionDTO> attributionOf(List<ParkingDTO> results) {
        return results.stream()
            .map(ParkingDTO::getCityId)
            .distinct()
            .map(cityId -> {
                ParkingIngestionProperties.Source source = properties.getSources().get(cityId);
                if (source == null || source.getAttribution() == null) {
                    return null;
                }
                ParkingIngestionProperties.Attribution a = source.getAttribution();
                return new AttributionDTO(cityId, a.getSource(), a.getLicense(), a.getUrl());
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Computes the response ETag: a hash of every served field of every result, so any change to the served data changes the tag.
     * A client revalidating with {@code If-None-Match} then gets {@code 304 Not Modified} before any serialization. A quoted strong validator per RFC 7232.
     *
     * @param results the parking DTOs to inspect
     * @return the ETag value for the response, quotes included
     */
    private static String dataVersion(List<ParkingDTO> results) {
        String fingerprint = results.stream()
            .map(ParkingController::fingerprint)
            .sorted()
            .collect(java.util.stream.Collectors.joining(","));
        return "\"" + Integer.toHexString(fingerprint.hashCode()) + "\"";
    }

    /**
     * Joins every served field of a parking into one comparable token (for the ETag hash).
     *
     * @param p the parking DTO
     * @return the field fingerprint
     */
    private static String fingerprint(ParkingDTO p) {
        return p.getId()
                + '|' + p.getName()
                + '|' + p.getLocation()
                + '|' + p.getCapacity()
                + '|' + p.getAvailableSpots()
                + '|' + p.getStatus()
                + '|' + p.getSourceUpdatedAt().toEpochMilli();
    }
}

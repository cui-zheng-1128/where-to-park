package com.wheretopark.controller;

import lombok.RequiredArgsConstructor;

import com.wheretopark.configuration.ParkingIngestionProperties;
import com.wheretopark.dto.AttributionDTO;
import com.wheretopark.dto.CityDTO;
import com.wheretopark.ingestion.IngestionMetrics;
import com.wheretopark.model.Parking;
import com.wheretopark.repository.ParkingRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Discovery endpoint: the cities covered by the API, with the freshness of the data currently served for each.
 * Lets a client enumerate supported cities and detect stale data without issuing a nearby query.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/v1/cities", produces = "application/json")
public class CityController {

    private final ParkingRepository parkingRepository;
    private final ParkingIngestionProperties properties;
    private final IngestionMetrics metrics;

    /**
     * Lists the supported cities with their attribution, parking count and data freshness.
     *
     * @return the cities, sorted by id
     */
    @GetMapping
    public List<CityDTO> cities() {
        Map<String, Long> countByCity = parkingRepository.findAll()
            .stream()
            .collect(Collectors.groupingBy(Parking::cityId, Collectors.counting()));

        return properties.getSources()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().isEnabled())
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> toDTO(entry.getKey(), entry.getValue(), countByCity))
            .toList();
    }

    /**
     * Maps one configured source to its DTO, resolving the freshness against the poll interval.
     *
     * @param cityId the city identifier
     * @param source the source configuration
     * @param countByCity the parking count per city
     * @return the city DTO
     */
    private CityDTO toDTO(String cityId, ParkingIngestionProperties.Source source, Map<String, Long> countByCity) {
        ParkingIngestionProperties.Attribution a = source.getAttribution();
        AttributionDTO attribution = a == null
            ? null
            : new AttributionDTO(cityId, a.getSource(), a.getLicense(), a.getUrl());

        // Fresh = the last success is within two poll intervals (one missed refresh tolerated).
        boolean fresh = metrics.isFresh(cityId, source.pollIntervalOrDefault().multipliedBy(2));
        return new CityDTO(cityId, attribution, countByCity.getOrDefault(cityId, 0L).intValue(), fresh);
    }
}

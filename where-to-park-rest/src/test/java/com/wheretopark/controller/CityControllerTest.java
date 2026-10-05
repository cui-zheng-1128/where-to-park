package com.wheretopark.controller;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.wheretopark.configuration.ParkingIngestionProperties;
import com.wheretopark.dto.CityDTO;
import com.wheretopark.ingestion.IngestionMetrics;
import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;
import com.wheretopark.repository.ParkingRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link CityController}.
 */
class CityControllerTest {

    private static final Instant T0 = Instant.parse("2026-09-28T18:37:33Z");

    @Test
    void shouldListEnabledCitiesWithCountAttributionAndFreshness() {
        ParkingIngestionProperties properties = new ParkingIngestionProperties();
        properties.setSources(Map.of(
            "poitiers",
            source(true),
            "nantes",
            source(true),
            "hidden",
            source(false)));
        ParkingRepository repository = repository(
            parking("poitiers", "1"),
            parking("poitiers", "2"),
            parking("nantes", "9"));
        // Fixed clock at T0 + 30 s, so a success at T0 is fresh within the default 60 s poll interval.
        Clock clock = Clock.fixed(T0.plusSeconds(30), ZoneOffset.UTC);
        IngestionMetrics metrics = new IngestionMetrics(new SimpleMeterRegistry(), clock);
        metrics.recordSuccess("poitiers", T0); // nantes never succeeded -> stale

        List<CityDTO> cities = new CityController(repository, properties, metrics).cities();

        assertThat(cities).extracting(CityDTO::getCityId)
            .containsExactly("nantes", "poitiers"); // sorted, disabled excluded
        assertThat(cities.get(1).getParkingCount()).isEqualTo(2);
        assertThat(cities.get(1).isFresh()).isTrue();
        assertThat(cities.get(0).isFresh()).isFalse(); // nantes has no successful ingestion
        assertThat(cities.get(1).getAttribution().getSource()).isEqualTo("Grand Poitiers - open data");
    }

    @Test
    void shouldReportStaleWhenLastSuccessIsOlderThanTwoPollIntervals() {
        ParkingIngestionProperties properties = new ParkingIngestionProperties();
        properties.setSources(Map.of("poitiers", source(true)));
        ParkingRepository repository = repository(parking("poitiers", "1"));
        // Fixed clock at T0 + 3 min: the success at T0 is beyond the 2Ã—60 s freshness window.
        Clock clock = Clock.fixed(T0.plusSeconds(180), ZoneOffset.UTC);
        IngestionMetrics metrics = new IngestionMetrics(new SimpleMeterRegistry(), clock);
        metrics.recordSuccess("poitiers", T0);

        List<CityDTO> cities = new CityController(repository, properties, metrics).cities();

        assertThat(cities).singleElement().satisfies(c -> assertThat(c.isFresh()).isFalse());
    }

    private static ParkingIngestionProperties.Source source(boolean enabled) {
        ParkingIngestionProperties.Attribution attribution = new ParkingIngestionProperties.Attribution();
        attribution.setSource("Grand Poitiers - open data");
        attribution.setLicense("Licence Ouverte 2.0");
        attribution.setUrl("https://example.fr");
        ParkingIngestionProperties.Source source = new ParkingIngestionProperties.Source();
        source.setEnabled(enabled);
        source.setAttribution(attribution);
        return source;
    }

    private static Parking parking(String cityId, String sourceId) {
        return new Parking(cityId + ":" + sourceId, cityId, "P", new Location(46.58, 0.34),
            100, 10, ParkingStatus.OPEN, T0, T0);
    }

    private static ParkingRepository repository(Parking... parkings) {
        List<Parking> snapshot = List.of(parkings);
        return new ParkingRepository() {
            @Override
            public List<Parking> findAll() {
                return snapshot;
            }

            @Override
            public Optional<Parking> findById(String id) {
                return snapshot.stream().filter(p -> p.id().equals(id)).findFirst();
            }
        };
    }
}

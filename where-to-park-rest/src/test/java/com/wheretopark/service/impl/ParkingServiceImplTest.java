package com.wheretopark.service.impl;

import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;
import com.wheretopark.repository.ParkingRepository;
import com.wheretopark.service.model.Ranking;
import com.wheretopark.service.model.SearchResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link ParkingServiceImpl}.
 */
class ParkingServiceImplTest {

    // a: at origin, 5 spots | b: ~1.1 km away, 10 spots | c: ~111 km away (out of any test radius)
    private ParkingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ParkingServiceImpl(repository(
            parking("a", 0, 0, 5),
            parking("b", 0, 0.01, 10),
            parking("c", 0, 1.0, 99)));
    }

    @Test
    void shouldFilterByRadiusAndSortByDistanceAscending() {
        SearchResult result = service.findNearby(new Location(0, 0), 2_000, 20, Ranking.DISTANCE);

        assertThat(result.getAppliedRanking()).isEqualTo(Ranking.DISTANCE);
        assertThat(result.getResults()).extracting(scored -> scored.getParking().id())
            .containsExactly("poitiers:a", "poitiers:b");
    }

    @Test
    void shouldPutLargestAvailabilityFirstWithDistanceTieBreak() {
        SearchResult result = service.findNearby(new Location(0, 0), 2_000, 20, Ranking.AVAILABILITY);

        assertThat(result.getAppliedRanking()).isEqualTo(Ranking.AVAILABILITY);
        assertThat(result.getResults()).extracting(scored -> scored.getParking().id())
            .containsExactly("poitiers:b", "poitiers:a");
    }

    @Test
    void shouldRankUnknownAvailabilityBelowAnyKnownFigure() {
        service = new ParkingServiceImpl(repository(
            parking("known", 0, 0, 0),      // 0 spots (full) is a known figure
            parking("unknown", 0, 0, null))); // null availability is unknown

        SearchResult result = service.findNearby(new Location(0, 0), 2_000, 20, Ranking.AVAILABILITY);

        assertThat(result.getResults()).extracting(scored -> scored.getParking().id())
            .containsExactly("poitiers:known", "poitiers:unknown");
    }

    @Test
    void shouldBreakTieOnIdWhenDistanceAndAvailabilityAreEqual() {
        service = new ParkingServiceImpl(repository(
            parking("b", 0, 0, 5),
            parking("a", 0, 0, 5)));

        SearchResult result = service.findNearby(new Location(0, 0), 2_000, 20, Ranking.DISTANCE);

        assertThat(result.getResults()).extracting(scored -> scored.getParking().id())
            .containsExactly("poitiers:a", "poitiers:b");
    }

    @Test
    void shouldDegradeDriveTimeToDistanceUntilRoutingEngineExists() {
        SearchResult result = service.findNearby(new Location(0, 0), 2_000, 20, Ranking.DRIVE_TIME);

        assertThat(result.getAppliedRanking()).isEqualTo(Ranking.DISTANCE);
    }

    @Test
    void shouldCapTheResultListAtLimit() {
        SearchResult result = service.findNearby(new Location(0, 0), 2_000, 1, Ranking.DISTANCE);

        assertThat(result.getResults()).hasSize(1);
    }

    @Test
    void shouldYieldEmptyResultsWhenStoreIsEmpty() {
        service = new ParkingServiceImpl(repository());

        SearchResult result = service.findNearby(new Location(0, 0), 2_000, 20, Ranking.DISTANCE);

        assertThat(result.getResults()).isEmpty();
    }

    @Test
    void shouldReturnTheParkingForAKnownId() {
        assertThat(service.getById("poitiers:a").id()).isEqualTo("poitiers:a");
    }

    @Test
    void shouldRejectAnUnknownId() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getById("poitiers:zzz"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown parking");
    }

    /** A repository serving a fixed snapshot (findById unused by findNearby). */
    private static ParkingRepository repository(Parking... parkings) {
        List<Parking> snapshot = List.of(parkings);
        return new ParkingRepository() {
            @Override
            public List<Parking> findAll() {
                return snapshot;
            }

            @Override
            public java.util.Optional<Parking> findById(String id) {
                return snapshot.stream().filter(p -> p.id().equals(id)).findFirst();
            }
        };
    }

    private static Parking parking(String id, double lat, double lon, Integer availableSpots) {
        return new Parking("poitiers:" + id, "poitiers", id, new Location(lat, lon), 100, availableSpots,
            ParkingStatus.OPEN, Instant.parse("2026-09-28T18:37:33Z"), Instant.parse("2026-09-28T18:37:33Z"));
    }
}

package com.wheretopark.repository.jpa;

import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence tests of {@link H2ParkingRepository} against an in-memory H2 database.
 * The schema is created by Hibernate here ({@code ddl-auto=create-drop}); in the real application it is owned by Flyway migrations and Hibernate never alters it.
 */
@DataJpaTest
@Import(H2ParkingRepository.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class H2ParkingRepositoryTest {

    @Autowired
    private H2ParkingRepository repository;

    @Test
    void replacesTheWholeSnapshotOfOneCity() {
        repository.replaceAll("poitiers", List.of(parking("poitiers", "1")));
        repository.replaceAll("poitiers", List.of(parking("poitiers", "2"), parking("poitiers", "3")));

        List<Parking> all = repository.findAll();

        assertThat(all).extracting(Parking::id).containsExactlyInAnyOrder("poitiers:2", "poitiers:3");
    }

    @Test
    void replacingOneCityLeavesOtherCitiesUntouched() {
        repository.replaceAll("poitiers", List.of(parking("poitiers", "1")));
        repository.replaceAll("nantes", List.of(parking("nantes", "9")));

        List<Parking> all = repository.findAll();

        assertThat(all).extracting(Parking::cityId).containsExactlyInAnyOrder("poitiers", "nantes");
    }

    @Test
    void roundTripsTheCanonicalModel() {
        Instant fetchedAt = Instant.parse("2026-09-28T18:40:00Z");
        Parking original = new Parking("poitiers:1", "poitiers", "NOTRE DAME",
            new Location(46.583, 0.345), 146, 53, ParkingStatus.OPEN,
            Instant.parse("2026-09-28T18:37:32Z"), fetchedAt);

        repository.replaceAll("poitiers", List.of(original));

        assertThat(repository.findAll()).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo("poitiers:1");
            assertThat(p.location()).isEqualTo(new Location(46.583, 0.345));
            assertThat(p.capacity()).isEqualTo(146);
            assertThat(p.availableSpots()).isEqualTo(53);
            assertThat(p.status()).isEqualTo(ParkingStatus.OPEN);
            assertThat(p.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-09-28T18:37:32Z"));
            // fetchedAt is persisted for staleness tracking.
            assertThat(p.fetchedAt()).isEqualTo(fetchedAt);
        });
    }

    @Test
    void purgeHistoryOlderThanDeletesOnlyOldRows() {
        repository.replaceAll("poitiers", List.of(parking("poitiers", "1")));

        long removed = repository.purgeHistoryOlderThan(Instant.parse("2026-09-29T00:00:00Z"));

        assertThat(removed).isEqualTo(1);
        // The latest-state table is untouched; only the history rows are gone.
        assertThat(repository.findAll()).hasSize(1);
    }

    private static Parking parking(String cityId, String sourceId) {
        return new Parking(cityId + ":" + sourceId, cityId, "P", new Location(46.58, 0.34),
            100, 10, ParkingStatus.OPEN, Instant.parse("2026-09-28T18:37:33Z"), Instant.parse("2026-09-28T18:37:33Z"));
    }
}

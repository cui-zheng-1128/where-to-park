package com.wheretopark.ingestion;

import com.wheretopark.repository.jpa.H2ParkingRepository;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link HistoryRetentionJob}.
 */
class HistoryRetentionJobTest {

    private static final Instant T0 = Instant.parse("2026-10-05T03:00:00Z");

    @Test
    void purgesRowsOlderThanTheRetentionWindow() {
        AtomicReference<Instant> cutoff = new AtomicReference<>();
        H2ParkingRepository store = purgeRecorder(cutoff);
        Clock fixed = Clock.fixed(T0, ZoneOffset.UTC);
        HistoryRetentionJob job = new HistoryRetentionJob(store, fixed, Duration.ofDays(30));

        job.purgeHistory();

        assertThat(cutoff.get()).isEqualTo(T0.minus(Duration.ofDays(30)));
    }

    /** A store recording the cutoff it is asked to purge from. */
    private static H2ParkingRepository purgeRecorder(AtomicReference<Instant> cutoff) {
        return new H2ParkingRepository(null, null) {
            @Override
            public long purgeHistoryOlderThan(Instant c) {
                cutoff.set(c);
                return 0;
            }
        };
    }
}

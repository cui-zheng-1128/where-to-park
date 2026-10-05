package com.wheretopark.ingestion;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link IngestionMetrics}.
 */
class IngestionMetricsTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-09-28T18:40:00Z"), ZoneOffset.UTC);

    @Test
    void aCityWithNoSuccessReportsZeroAndMinusOne() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IngestionMetrics metrics = new IngestionMetrics(registry, FIXED);

        metrics.registerCity("poitiers");

        assertThat(gauge(registry, "parking_ingestion_last_success_timestamp", "poitiers")).isEqualTo(0.0);
        assertThat(gauge(registry, "parking_ingestion_stale_seconds", "poitiers")).isEqualTo(-1.0);
    }

    @Test
    void recordSuccessUpdatesLastSuccessAndStaleAge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IngestionMetrics metrics = new IngestionMetrics(registry, FIXED);
        metrics.registerCity("nantes");

        Instant success = Instant.parse("2026-09-28T18:37:33Z"); // 147 s before the fixed clock
        metrics.recordSuccess("nantes", success);

        assertThat(metrics.lastSuccess("nantes")).isEqualTo(success);
        assertThat(gauge(registry, "parking_ingestion_last_success_timestamp", "nantes")).isEqualTo(success.getEpochSecond());
        assertThat(gauge(registry, "parking_ingestion_stale_seconds", "nantes")).isEqualTo(147.0);
    }

    private static double gauge(SimpleMeterRegistry registry, String name, String city) {
        return registry.get(name).tag("city", city).gauge().value();
    }
}

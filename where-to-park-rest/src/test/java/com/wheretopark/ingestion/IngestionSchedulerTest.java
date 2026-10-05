package com.wheretopark.ingestion;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.wheretopark.connector.ConnectorRegistry;
import com.wheretopark.connector.ParkingSourceConnector;
import com.wheretopark.connector.ParkingStore;
import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link IngestionScheduler}. Polls run on a pool, so assertions wait on latches rather than assuming synchronous execution.
 */
class IngestionSchedulerTest {

    private static final Instant T0 = Instant.parse("2026-09-28T18:37:33Z");

    private IngestionScheduler scheduler;
    private final java.util.Queue<String> replacedCities = new ConcurrentLinkedQueue<>();
    private IngestionMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new IngestionMetrics(new SimpleMeterRegistry(), java.time.Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void pollsAndStoresEachConnector() throws Exception {
        CountDownLatch stored = new CountDownLatch(1);
        scheduler = scheduler(connector("poitiers", null), store(stored));

        scheduler.pollDueConnectors();

        assertThat(stored.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(replacedCities).contains("poitiers");
        assertThat(metrics.lastSuccess("poitiers")).isNotNull();
    }

    @Test
    void backsOffAConnectorUntilItsPollIntervalElapses() throws Exception {
        CountDownLatch firstStore = new CountDownLatch(1);
        ParkingSourceConnector connector = connector("poitiers", null);
        scheduler = scheduler(connector, store(firstStore));

        scheduler.pollDueConnectors();
        assertThat(firstStore.await(5, TimeUnit.SECONDS)).isTrue();
        replacedCities.clear();

        scheduler.pollDueConnectors(); // within the 60 s poll interval: not due

        assertThat(replacedCities).isEmpty();
    }

    @Test
    void backsOffExponentiallyOnFailureAndKeepsStaleData() throws Exception {
        AtomicInteger fetchCalls = new AtomicInteger();
        ParkingSourceConnector failing = failingConnector("broken", fetchCalls);
        scheduler = scheduler(failing, store(new CountDownLatch(0)));

        scheduler.pollDueConnectors();
        waitFor(() -> fetchCalls.get() >= 1);
        scheduler.pollDueConnectors(); // inside the back-off window: not retried
        scheduler.pollDueConnectors();

        assertThat(fetchCalls.get()).isEqualTo(1);
        assertThat(replacedCities).isEmpty();
        assertThat(metrics.lastSuccess("broken")).isNull();
    }

    // ---- helpers ---------------------------------------------------------------

    private IngestionScheduler scheduler(ParkingSourceConnector connector, ParkingStore store) {
        return new IngestionScheduler(new ConnectorRegistry(List.of(connector)), store,
            metrics, java.time.Clock.systemUTC(), 2, Duration.ofMinutes(5));
    }

    private ParkingStore store(CountDownLatch latch) {
        return (cityId, parkings) -> {
            replacedCities.add(cityId);
            latch.countDown();
        };
    }

    private ParkingSourceConnector connector(String cityId, CountDownLatch latch) {
        return new ParkingSourceConnector() {
            @Override
            public String cityId() {
                return cityId;
            }

            @Override
            public Duration pollInterval() {
                return Duration.ofSeconds(60);
            }

            @Override
            public List<Parking> fetch() {
                if (latch != null) {
                    latch.countDown();
                }
                return List.of(new Parking(cityId + ":1", cityId, "P", new Location(46.58, 0.34),
                    100, 10, ParkingStatus.OPEN, T0, T0));
            }
        };
    }

    private ParkingSourceConnector failingConnector(String cityId, AtomicInteger calls) {
        return new ParkingSourceConnector() {
            @Override
            public String cityId() {
                return cityId;
            }

            @Override
            public Duration pollInterval() {
                return Duration.ofSeconds(60);
            }

            @Override
            public List<Parking> fetch() {
                calls.incrementAndGet();
                throw new RuntimeException("source is down");
            }
        };
    }

    private static void waitFor(Check check) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!check.ok() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    private interface Check {
        boolean ok();
    }
}

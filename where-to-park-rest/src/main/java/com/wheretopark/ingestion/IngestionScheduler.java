package com.wheretopark.ingestion;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import com.wheretopark.connector.ConnectorRegistry;
import com.wheretopark.connector.ParkingSourceConnector;
import com.wheretopark.connector.ParkingStore;
import com.wheretopark.model.Parking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Polling engine that decouples the read path from the city sources.
 *
 * <p>
 * Each connector is polled on its own schedule: at most once per its {@code pollInterval}, and â€” on failure â€” with exponential back-off (30 s, 60 s, 120 s, ... capped at {@code parking.ingestion.max-backoff}).
 * A failing source only logs a warning and updates the staleness metrics: the store keeps serving the last successful snapshot (stale serving), so an upstream outage is invisible to API clients.
 *
 * <p>
 * Connectors run on a small bounded pool, in parallel, so one slow city never delays the refresh of the others.
 * A city whose previous fetch is still running is skipped (in-flight guard), so two overlapping snapshots can never let the older one win.
 */
@Slf4j
@Component
public class IngestionScheduler {

    private final ConnectorRegistry registry;
    private final ParkingStore store;
    private final IngestionMetrics metrics;
    private final Clock clock;
    private final Duration maxBackoff;
    private final ExecutorService executor;

    private final Map<String, Instant> lastAttempt = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, Instant> backoffUntil = new ConcurrentHashMap<>();
    private final java.util.Set<String> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Creates the scheduler and registers one gauge pair per connector.
     *
     * @param registry the active connectors
     * @param store the write port of the parking store
     * @param metrics the ingestion metrics
     * @param clock the time source
     * @param parallelism the connector pool size
     * @param maxBackoff the cap on the exponential failure back-off
     */
    public IngestionScheduler(ConnectorRegistry registry,
            ParkingStore store,
            IngestionMetrics metrics,
            Clock clock,
            @Value("${parking.ingestion.parallelism:4}") int parallelism,
            @Value("${parking.ingestion.max-backoff:PT5M}") Duration maxBackoff) {
        this.registry = registry;
        this.store = store;
        this.metrics = metrics;
        this.clock = clock;
        this.maxBackoff = maxBackoff;
        this.executor = Executors.newFixedThreadPool(parallelism);
        registry.connectors().forEach(connector -> metrics.registerCity(connector.cityId()));
    }

    /**
     * Kicks off a first poll at startup so the store is warm before traffic arrives.
     */
    @EventListener(ApplicationReadyEvent.class)
    void warmup() {
        pollDueConnectors();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /**
     * Polls every connector that is due, on the pool.
     */
    @Scheduled(fixedDelayString = "${parking.ingestion.tick:PT15S}")
    public void pollDueConnectors() {
        for (ParkingSourceConnector connector : registry.connectors()) {
            // Skip a city whose previous fetch is still running (slow source), so two overlapping replaceAll transactions can never let the older snapshot win.
            if (isDue(connector.cityId(), connector.pollInterval()) && inFlight.add(connector.cityId())) {
                executor.submit(() -> poll(connector));
            }
        }
    }

    /**
     * A connector is due when its poll interval has elapsed since the last attempt AND any failure back-off has expired.
     *
     * @param cityId the city identifier
     * @param pollInterval the connector poll interval
     * @return whether the connector is due
     */
    private boolean isDue(String cityId, Duration pollInterval) {
        Instant now = Instant.now(clock);
        Instant until = backoffUntil.get(cityId);

        if (until != null && now.isBefore(until)) {
            return false;
        }

        Instant last = lastAttempt.get(cityId);
        return last == null || !last.plus(pollInterval).isAfter(now);
    }

    /**
     * Polls one connector and stores its snapshot, or backs off on failure.
     *
     * @param connector the connector to poll
     */
    private void poll(ParkingSourceConnector connector) {
        String cityId = connector.cityId();
        try {
            Instant now = Instant.now(clock);
            lastAttempt.put(cityId, now);
            List<Parking> parkings = connector.fetch();
            store.replaceAll(cityId, parkings);
            consecutiveFailures.remove(cityId);
            backoffUntil.remove(cityId);
            metrics.recordSuccess(cityId, now);
            log.info("Ingested {} parkings for city {}", parkings.size(), cityId);
        } catch (Exception e) {
            int failures = consecutiveFailures.merge(cityId, 1, Integer::sum);
            Duration backoff = backoff(failures);
            backoffUntil.put(cityId, Instant.now(clock).plus(backoff));
            log.warn("Ingestion failed for city {}: {} (serving stale data, retry in {}s)", cityId, e, backoff.getSeconds());
        } finally {
            inFlight.remove(cityId);
        }
    }

    /**
     * Exponential back-off: 30 s, doubling per consecutive failure, capped at max-backoff.
     *
     * @param consecutiveFailuresCount the number of consecutive failures
     * @return the back-off delay
     */
    private Duration backoff(int consecutiveFailuresCount) {
        long seconds = 30L << Math.min(consecutiveFailuresCount - 1, 6); // 30s, 60s, 120s, ...
        Duration candidate = Duration.ofSeconds(seconds);
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }
}

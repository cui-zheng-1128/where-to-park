package com.wheretopark.ingestion;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

/**
 * Business observability of the ingestion pipeline: a failing source must be visible to operators (metrics + alerting), not buried in a warn log.
 * Two gauges are registered per city via {@link #registerCity}:
 *
 * <ul>
 * <li>{@code parking_ingestion_last_success_timestamp{city}} â€” epoch seconds of the last successful snapshot; a flat/stuck value means the source is down (alert on it);</li>
 * <li>{@code parking_ingestion_stale_seconds{city}} â€” age of the data currently served (now âˆ’ last success); grows while a source is failing (alert on a threshold).</li>
 * </ul>
 */
@Component
public class IngestionMetrics {

    private final MeterRegistry registry;
    private final Clock clock;
    private final Map<String, Instant> lastSuccess = new ConcurrentHashMap<>();

    /**
     * Creates the metrics holder.
     *
     * @param registry the meter registry to bind gauges to
     * @param clock the time source
     */
    public IngestionMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * Registers the two gauges of one city. The gauges read the live map, so a city that has not succeeded yet reports 0 / -1 until its first success.
     *
     * @param cityId the city identifier
     */
    public void registerCity(String cityId) {
        bind("parking_ingestion_last_success_timestamp", cityId, city -> lastSuccess.get(city) == null ? 0.0 : lastSuccess.get(city).getEpochSecond());
        bind("parking_ingestion_stale_seconds", cityId, city -> lastSuccess.get(city) == null ? -1.0 : Duration.between(lastSuccess.get(city), Instant.now(clock)).getSeconds());
    }

    /**
     * Records a successful ingestion for a city.
     *
     * @param cityId the city identifier
     * @param at when the snapshot was pulled
     */
    public void recordSuccess(String cityId, Instant at) {
        lastSuccess.put(cityId, at);
    }

    /**
     * Returns the last successful ingestion of a city.
     *
     * @param cityId the city identifier
     * @return the last success, or {@code null} if none succeeded yet
     */
    public Instant lastSuccess(String cityId) {
        return lastSuccess.get(cityId);
    }

    /**
     * Whether the data currently served for a city is fresh: the last successful snapshot is not older than {@code maxAge}. A city that has never succeeded is not fresh.
     *
     * @param cityId the city identifier
     * @param maxAge the age above which served data counts as stale
     */
    public boolean isFresh(String cityId, Duration maxAge) {
        Instant success = lastSuccess.get(cityId);
        return success != null && Duration.between(success, Instant.now(clock)).compareTo(maxAge) <= 0;
    }

    /**
     * Binds one gauge reading the live per-city value.
     *
     * @param name the gauge name
     * @param cityId the city tag
     * @param value the function reading the current value
     */
    private void bind(String name, String cityId, ToDoubleFunction<String> value) {
        Gauge.builder(name, () -> value.applyAsDouble(cityId))
            .tag("city", cityId)
            .register(registry);
    }
}

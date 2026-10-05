package com.wheretopark.ingestion;

import lombok.extern.slf4j.Slf4j;

import com.wheretopark.repository.jpa.H2ParkingRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Storage-lifecycle job: bounds the occupancy history table by deleting observations older than the retention window.
 * Kept apart from {@link IngestionScheduler} â€” polling sources and pruning the archive are independent concerns.
 */
@Slf4j
@Component
public class HistoryRetentionJob {

    private final H2ParkingRepository store;
    private final Clock clock;
    private final Duration retention;

    /**
     * Creates the job.
     *
     * @param store the parking store adapter exposing history purge
     * @param clock the time source
     * @param retention the age above which history rows are deleted
     */
    public HistoryRetentionJob(H2ParkingRepository store,
            Clock clock,
            @Value("${parking.ingestion.history-retention:P30D}") Duration retention) {
        this.store = store;
        this.clock = clock;
        this.retention = retention;
    }

    /**
     * Purges occupancy history older than the retention window.
     */
    @Scheduled(cron = "${parking.ingestion.history-purge-cron:0 0 3 * * *}")
    public void purgeHistory() {
        long removed = store.purgeHistoryOlderThan(Instant.now(clock).minus(retention));

        if (removed > 0) {
            log.info("Purged {} history rows older than {}", removed, retention);
        }
    }
}

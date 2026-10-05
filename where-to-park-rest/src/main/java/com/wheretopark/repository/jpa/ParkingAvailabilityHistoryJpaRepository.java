package com.wheretopark.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/**
 * Technical interface: Spring Data JPA operations on the occupancy history.
 */
public interface ParkingAvailabilityHistoryJpaRepository extends JpaRepository<ParkingAvailabilityHistoryEntity, ParkingAvailabilityHistoryEntity.Pk> {

    /**
     * Deletes observations older than the cutoff (retention). Returns the deleted row count.
     * 
     * @param cutoff the cutoff timestamp (exclusive)
     * @return the number of deleted rows
     */
    long deleteByFetchedAtBefore(Instant cutoff);
}

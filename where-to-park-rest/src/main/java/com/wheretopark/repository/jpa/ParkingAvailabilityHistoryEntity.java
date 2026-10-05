package com.wheretopark.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.wheretopark.model.Parking;

import java.io.Serializable;
import java.time.Instant;

/**
 * One occupancy observation of a parking at a fetch time. The append-only history feeding availability forecasting (P3); kept separate from the latest-state {@link ParkingEntity}.
 */
@Entity
@Table(name = "parking_availability_history")
@IdClass(ParkingAvailabilityHistoryEntity.Pk.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingAvailabilityHistoryEntity {

    @Id
    @Column(name = "parking_id", length = 128)
    private String parkingId;

    @Id
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "available_spots")
    private Integer availableSpots;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /**
     * Builds a history row from the canonical model.
     *
     * @param parking the canonical parking
     * @return the history entity
     */
    static ParkingAvailabilityHistoryEntity fromModel(Parking parking) {
        ParkingAvailabilityHistoryEntity entity = new ParkingAvailabilityHistoryEntity();
        entity.parkingId = parking.id();
        entity.fetchedAt = parking.fetchedAt();
        entity.availableSpots = parking.availableSpots();
        entity.status = parking.status().name();
        return entity;
    }

    /**
     * Composite primary key.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private String parkingId;
        private Instant fetchedAt;
    }
}

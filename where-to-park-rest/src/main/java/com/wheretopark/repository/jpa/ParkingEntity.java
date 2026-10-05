package com.wheretopark.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;

import java.time.Instant;

/**
 * JPA storage form of the canonical {@link Parking} model.
 *
 * <p>
 * Coordinates are stored as plain WGS84 {@code lat}/{@code lon} doubles on purpose:
 * at this data volume (hundreds of rows per city) the read path loads the snapshot and computes haversine distances in memory anyway,
 * so a spatial column would add dialect fragility for zero benefit. When a dataset outgrows a full scan, the scale-out target is PostGIS (see project docs), not spatial-H2.
 */
@Entity
@Table(name = "parking", indexes = @Index(name = "idx_parking_city", columnList = "city_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(name = "city_id", nullable = false, length = 64)
    private String cityId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lon", nullable = false)
    private double lon;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "available_spots")
    private Integer availableSpots;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ParkingStatus status;

    @Column(name = "source_updated_at", nullable = false)
    private Instant sourceUpdatedAt;

    /** When this snapshot row was pulled from the source (staleness tracking). */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    /**
     * Builds an entity from the canonical model.
     *
     * @param parking the canonical parking
     * @return the JPA entity
     */
    static ParkingEntity fromModel(Parking parking) {
        ParkingEntity entity = new ParkingEntity();
        entity.id = parking.id();
        entity.cityId = parking.cityId();
        entity.name = parking.name();
        entity.lat = parking.location().lat();
        entity.lon = parking.location().lon();
        entity.capacity = parking.capacity();
        entity.availableSpots = parking.availableSpots();
        entity.status = parking.status();
        entity.sourceUpdatedAt = parking.sourceUpdatedAt();
        entity.fetchedAt = parking.fetchedAt();
        return entity;
    }

    /**
     * Rebuilds the canonical model from this entity.
     *
     * @return the canonical parking
     */
    Parking toModel() {
        return new Parking(id, cityId, name, new Location(lat, lon), capacity, availableSpots, status, sourceUpdatedAt, fetchedAt);
    }
}

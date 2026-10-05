package com.wheretopark.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Technical interface: Spring Data JPA operations on {@link ParkingEntity}.
 *
 * <p>
 * This is infrastructure-layer code, not used directly by business logic.
 * The business layer accesses the store through the domain port ({@link com.wheretopark.repository.ParkingRepository}), implemented by the adapter {@link H2ParkingRepository} which delegates to this interface.
 */
public interface ParkingJpaRepository extends JpaRepository<ParkingEntity, String> {

    /**
     * Deletes all parking of a city.
     *
     * @param cityId the city identifier
     */
    void deleteByCityId(String cityId);
}

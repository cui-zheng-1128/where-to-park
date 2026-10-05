package com.wheretopark.repository;

import com.wheretopark.model.Parking;

import java.util.List;
import java.util.Optional;

/**
 * Business port: read access to the parking store.
 *
 * <p>
 * This is the domain-layer abstraction. Business code depends only on this interface and has no knowledge of the underlying storage technology. The implementation is provided by an infrastructure adapter.
 */
public interface ParkingRepository {

    /**
     * Returns every known parking.
     *
     * @return existing parking list
     */
    List<Parking> findAll();

    /**
     * Returns one parking by its identifier, or empty when unknown.
     *
     * @param id the parking identifier
     * @return the matching parking, if present
     */
    Optional<Parking> findById(String id);
}

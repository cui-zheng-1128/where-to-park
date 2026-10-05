package com.wheretopark.repository.jpa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.wheretopark.connector.ParkingStore;
import com.wheretopark.model.Parking;
import com.wheretopark.repository.ParkingRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adapter: implements the business ports ({@link ParkingRepository} for reads, {@link ParkingStore} for writes) using JPA/H2 as the storage technology.
 *
 * <p>
 * This class bridges the domain layer (which knows nothing about databases) and the infrastructure layer (which knows nothing about business rules).
 * It converts between the canonical {@link Parking} model and the JPA {@link ParkingEntity}.
 *
 * <p>
 * {@link #replaceAll} deletes and reinserts inside a single transaction, so readers always see either the complete previous snapshot or the complete new one.
 *
 * <p>
 * Activated by default ({@code parking.storage=h2}). To switch to another storage backend, provide another implementation of the same two ports with a different {@code @ConditionalOnProperty} value.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "parking.storage", havingValue = "h2", matchIfMissing = true)
public class H2ParkingRepository implements ParkingRepository, ParkingStore {

    private final ParkingJpaRepository jpa;
    private final ParkingAvailabilityHistoryJpaRepository historyJpa;

    /**
     * {@inheritDoc}
     * Reads all rows and maps them to the canonical model.
     * 
     * @return the list of all parking
     */
    @Override
    @Transactional(readOnly = true)
    public List<Parking> findAll() {
        return jpa.findAll().stream().map(ParkingEntity::toModel).toList();
    }

    /**
     * {@inheritDoc}
     * Looks the id up in the parking table.
     * 
     * @param id the parking id
     * @return the parking if found, empty otherwise
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Parking> findById(String id) {
        return jpa.findById(id).map(ParkingEntity::toModel);
    }

    /**
     * {@inheritDoc}
     * Deletes the city's rows and reinserts the snapshot in one transaction, appending the occupancy observation to the forecasting time series in the same transaction.
     * 
     * @param cityId the city id
     * @param parkings the new snapshot of all parking in the city
     */
    @Override
    @Transactional
    public void replaceAll(String cityId, List<Parking> parkings) {
        jpa.deleteByCityId(cityId);
        jpa.saveAll(parkings.stream().map(ParkingEntity::fromModel).toList());
        historyJpa.saveAll(parkings.stream().map(ParkingAvailabilityHistoryEntity::fromModel).toList());
        log.debug("Replaced {} parking list for city {}", parkings.size(), cityId);
    }

    /**
     * Deletes occupancy observations older than the cutoff.
     *
     * @param cutoff the retention cutoff
     * @return the deleted row count
     */
    @Transactional
    public long purgeHistoryOlderThan(Instant cutoff) {
        return historyJpa.deleteByFetchedAtBefore(cutoff);
    }
}

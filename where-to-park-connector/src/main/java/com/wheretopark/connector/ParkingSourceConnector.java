package com.wheretopark.connector;

import com.wheretopark.model.Parking;

import java.time.Duration;
import java.util.List;

/**
 * SPI implemented once per city / source platform (Data Fair, Opendatasoft, ArcGIS REST, CKAN, DATEX II, ...).
 *
 * <p>
 * Contract:
 * <ul>
 * <li>{@link #fetch()} is invoked by the ingestion scheduler, never faster than {@link #pollInterval()};</li>
 * <li>the returned snapshot is fully normalized into the canonical model: no source-specific field names, units or coordinate reference systems may leak;</li>
 * <li>a broken source throws: the scheduler keeps serving the last successful snapshot, so the failure never propagates to the read path;</li>
 * <li>implementations are plain Java.</li>
 * </ul>
 */
public interface ParkingSourceConnector {

    /**
     * Returns the identifier of the city served by this connector.
     *
     * @return the city identifier, e.g. {@code "poitiers"}
     */
    String cityId();

    /**
     * Returns the minimum delay between two polls of this source.
     *
     * @return the poll interval
     */
    Duration pollInterval();

    /**
     * Fetches and normalizes the current snapshot of the source.
     *
     * @return the full snapshot for this city (replace semantics, not a delta)
     * @throws Exception any failure (network, HTTP error, payload drift): the caller keeps stale data
     */
    List<Parking> fetch() throws Exception;
}

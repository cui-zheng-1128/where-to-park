package com.wheretopark.connector;

import java.util.List;

/**
 * The set of active city connectors, built from configuration by the runtime module. A plain carrier so the scheduler has exactly one unambiguous injection point.
 */
public record ConnectorRegistry(List<ParkingSourceConnector> connectors) {

    /**
     * Creates the registry, failing fast on a null list and freezing it into an immutable copy.
     *
     * @param connectors the active connectors
     */
    public ConnectorRegistry {
        connectors = List.copyOf(connectors);
    }
}

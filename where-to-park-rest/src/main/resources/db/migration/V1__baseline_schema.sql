-- Canonical parking snapshot. One row per parking; a city snapshot is replaced atomically.
-- fetched_at tracks when the row was pulled from the source, so the stale data age can be observed per city (ingestion metrics / alerting).
CREATE TABLE parking (
    id                 VARCHAR(128) NOT NULL PRIMARY KEY,
    city_id            VARCHAR(64)  NOT NULL,
    name               VARCHAR(255) NOT NULL,
    lat                DOUBLE       NOT NULL,
    lon                DOUBLE       NOT NULL,
    capacity           INTEGER,
    available_spots    INTEGER,
    status             VARCHAR(16)  NOT NULL,
    source_updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    fetched_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_parking_city ON parking (city_id);

-- Occupancy history: one row per parking per fetch. The time series is the foundation for availability forecasting; the latest-state `parking` table alone discards it.
CREATE TABLE parking_availability_history (
    parking_id        VARCHAR(128) NOT NULL,
    fetched_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    available_spots   INTEGER,
    status            VARCHAR(16)  NOT NULL,
    PRIMARY KEY (parking_id, fetched_at)
);

CREATE INDEX idx_pah_fetched ON parking_availability_history (fetched_at);

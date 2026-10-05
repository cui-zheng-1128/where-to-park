package com.wheretopark.model;

/**
 * WGS84 geographic coordinates (EPSG:4326), the only coordinate reference system allowed to leak out of the canonical model.
 */
public record Location(double lat, double lon) {

    /**
     * Creates a location, validating the WGS84 ranges.
     *
     * @param lat the latitude, in [-90, 90]
     * @param lon the longitude, in [-180, 180]
     * @throws IllegalArgumentException when either coordinate is out of range
     */
    public Location {
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("lat out of range [-90, 90]: " + lat);
        }

        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("lon out of range [-180, 180]: " + lon);
        }
    }
}

package com.wheretopark.dto;

import lombok.Value;

/**
 * One city covered by the API, with the freshness of the data currently served for it. See {@code CityController} for the discovery purpose of this endpoint.
 */
@Value
public class CityDTO {

    String cityId;
    AttributionDTO attribution;

    // Parking currently known for this city.
    int parkingCount;

    // Whether the currently served data is fresh (last success within two poll intervals); {@code false} means it may be stale.
    boolean fresh;
}

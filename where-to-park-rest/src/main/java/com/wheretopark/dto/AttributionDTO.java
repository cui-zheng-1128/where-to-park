package com.wheretopark.dto;

import lombok.Value;

/**
 * Data-source attribution, embedded in every response carrying parking data so no frontend can "forget" to display it: every source license in use makes source credit legally mandatory.
 */
@Value
public class AttributionDTO {

    String cityId;
    String source;
    String license;
    String url;
}

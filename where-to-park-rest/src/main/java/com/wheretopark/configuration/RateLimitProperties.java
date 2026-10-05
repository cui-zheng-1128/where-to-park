package com.wheretopark.configuration;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate-limit settings for the public API. Guards the service against a single client exhausting it: without a cap, one consumer can burn all capacity on an unauthenticated public endpoint.
 */
@Data
@ConfigurationProperties(prefix = "parking.rate-limit")
public class RateLimitProperties {

    // Whether rate limiting is active.
    private boolean enabled = true;

    // Requests allowed per client per window.
    private int requests = 120;

    // Window length in seconds.
    private int windowSeconds = 60;

    // Soft cap on tracked client windows; above it, expired windows are evicted lazily on access.
    private int maxClients = 10_000;
}

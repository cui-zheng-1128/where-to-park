package com.wheretopark.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Time source. Injected rather than calling {@code Instant.now()} directly in the ingestion pipeline, so staleness/back-off logic can be tested with a fixed clock.
 */
@Configuration
public class ClockConfiguration {

    /**
     * Provides the UTC system clock.
     *
     * @return the system clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

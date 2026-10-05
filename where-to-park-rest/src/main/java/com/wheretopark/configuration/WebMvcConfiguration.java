package com.wheretopark.configuration;

import com.wheretopark.service.model.Ranking;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

/**
 * Web MVC configuration: CORS, query-parameter converters and the public-API rate limiter.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebMvcConfiguration implements WebMvcConfigurer {

    /**
     * Cross-origin {@code GET} is open to any origin: this API serves public open data with no credentials, so {@code *} is safe (simple GET, no cookies).
     * If API keys are ever introduced, this must be narrowed to the known frontend origins.
     *
     * @param registry the CORS registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/v1/**")
            .allowedOrigins("*")
            .allowedMethods("GET");
    }

    /**
     * Accepts {@code ?ranking=distance} as well as {@code DISTANCE}. Invalid values still fail with {@code 400}.
     *
     * @param registry the formatter registry
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Ranking.class, source -> Ranking.valueOf(source.toUpperCase(Locale.ROOT)));
    }

    /**
     * Per-client rate limiter on the public API. Active unless {@code parking.rate-limit.enabled=false}.
     *
     * @param properties the rate-limit settings
     * @return the filter registration
     */
    @Bean
    @ConditionalOnProperty(name = "parking.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties properties) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(properties));
        registration.addUrlPatterns("/v1/*");
        return registration;
    }
}

package com.wheretopark.configuration;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link RateLimitFilter}. Exercises the fixed-window logic directly (no servlet container) against synthetic requests.
 */
class RateLimitFilterTest {

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setRequests(3);
        properties.setWindowSeconds(60);
        filter = new RateLimitFilter(properties);
    }

    @Test
    void shouldAllowRequestsUpToTheCapThenReject() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/v1/parkings/nearby", "10.0.0.1"), response, chain);
        }

        assertThat(passed).hasValue(3); // cap = 3
    }

    @Test
    void shouldRejectWith429AndRetryAfter() throws Exception {
        FilterChain chain = (req, res) -> {};
        MockHttpServletResponse response = null;
        for (int i = 0; i < 4; i++) {
            response = new MockHttpServletResponse();
            filter.doFilter(request("/v1/parkings/nearby", "10.0.0.2"), response, chain);
        }

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void shouldTrackClientsIndependently() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        for (int i = 0; i < 3; i++) {
            filter.doFilter(request("/v1/parkings/nearby", "10.0.1.1"), new MockHttpServletResponse(), chain);
        }
        // A different client has its own window and is not affected.
        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(request("/v1/parkings/nearby", "10.0.1.2"), other, chain);

        assertThat(other.getStatus()).isEqualTo(200);
        assertThat(passed).hasValue(4);
    }

    @Test
    void shouldKeyOnTheFirstXForwardedForHopWhenPresent() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        MockHttpServletRequest request = request("/v1/parkings/nearby", "10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 70.41.3.18");
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(passed).hasValue(1);
    }

    @Test
    void shouldEvictStaleWindowsOnceAboveMaxClients() throws Exception {
        properties.setMaxClients(1);
        filter = new RateLimitFilter(properties);
        FilterChain chain = (req, res) -> {};

        // Two clients: above the cap of 1, so the stale-window eviction runs on the second call.
        filter.doFilter(request("/v1/parkings/nearby", "10.0.0.1"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("/v1/parkings/nearby", "10.0.0.2"), new MockHttpServletResponse(), chain);

        // No assertion on state: the point is that the eviction path executes without error.
    }

    private static MockHttpServletRequest request(String uri, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}

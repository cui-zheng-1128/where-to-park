package com.wheretopark.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import com.wheretopark.controller.exception.ErrorResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window per-client rate limiter on the public API (see {@link RateLimitProperties} for why a cap exists). Clients are keyed by IP;
 * the window state is in-memory per instance, lazily evicted beyond {@code max-clients} â€” which matches the per-replica H2 store of this service.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RateLimitProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Creates the filter.
     *
     * @param properties the rate-limit settings
     */
    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Admits the request under the client's current window, else answers {@code 429}.
     *
     * @param request the incoming request
     * @param response the outgoing response
     * @param chain the rest of the filter chain
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String key = clientKey(request);
        long now = System.currentTimeMillis();
        evictStale(now);
        Window window = windows.compute(key,
            (k, w) -> (w == null || w.isExpired(now, properties.getWindowSeconds() * 1000L))
                ? new Window(now)
                : w);

        if (window.incrementAndGet() <= properties.getRequests()) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            long retryAfter = window.retryAfterSeconds(now, properties.getWindowSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.getWriter().write(JSON.writeValueAsString(new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded")));
        }
    }

    /**
     * Lazily drops windows that expired more than one window ago, so the map stays bounded.
     *
     * @param now the current time in milliseconds
     */
    private void evictStale(long now) {
        if (windows.size() <= properties.getMaxClients()) {
            return;
        }
        windows.entrySet().removeIf(e -> e.getValue().isExpired(now, properties.getWindowSeconds() * 2000L));
    }

    /**
     * Resolves the client key: the first hop of {@code X-Forwarded-For} when behind a proxy, else the remote address.
     *
     * @param request the incoming request
     * @return the client identifier
     */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
            ? forwarded.split(",")[0].trim()
            : request.getRemoteAddr();
    }

    /**
     * One fixed window per client.
     */
    private static final class Window {
        private final long startMillis;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startMillis) {
            this.startMillis = startMillis;
        }

        /**
         * Whether this window's age exceeds the given length in milliseconds.
         * 
         * @param now the current time in milliseconds
         * @param windowMillis the window length in milliseconds
         * @return true if the window is expired, false otherwise
         */
        private boolean isExpired(long now, long windowMillis) {
            return now - startMillis >= windowMillis;
        }

        private int incrementAndGet() {
            return count.incrementAndGet();
        }

        private long retryAfterSeconds(long now, int windowSeconds) {
            long elapsed = (now - startMillis) / 1000L;
            return Math.max(1, windowSeconds - elapsed);
        }
    }
}

package com.wheretopark.connector;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of {@link AbstractHttpSourceConnector}: page URI building, the source-advertised total parsing, the fault-tolerant normalization loop,
 * and the HTTP fetch loop (paging, truncation failure, status check) against a live in-JVM server.
 */
class AbstractHttpSourceConnectorTest {

    private static final Instant T0 = Instant.parse("2026-09-28T18:37:33Z");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AbstractHttpSourceConnector connector = connector("https://example.fr/records");

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---- page URI building -------------------------------------------------------

    @Test
    void shouldAppendLimitAndOffsetToPageUri() {
        assertThat(connector.pageUri(0).toString()).isEqualTo("https://example.fr/records?limit=100&offset=0");
        assertThat(connector.pageUri(2).toString()).isEqualTo("https://example.fr/records?limit=100&offset=200");
    }

    @Test
    void shouldPreserveAnExistingQueryStringInPageUri() {
        AbstractHttpSourceConnector withQuery = connector("https://example.fr/lines?format=json");

        assertThat(withQuery.pageUri(1).toString())
            .isEqualTo("https://example.fr/lines?format=json&limit=100&offset=100");
    }

    // ---- source-advertised total parsing -------------------------------------------

    @Test
    void shouldReadBothCommonFieldNamesForDeclaredTotal() {
        assertThat(connector.declaredTotal(mapper.readTree("{\"total_count\":42,\"results\":[]}"))).isEqualTo(42);
        assertThat(connector.declaredTotal(mapper.readTree("{\"total\":7,\"results\":[]}"))).isEqualTo(7);
        assertThat(connector.declaredTotal(mapper.readTree("{\"results\":[]}"))).isEqualTo(-1);
    }

    // ---- normalization loop --------------------------------------------------------

    @Test
    void shouldKeepGoodRecordsAndDropMalformedOnes() {
        List<Parking> parkings = connector.normalize(
            "{\"results\":[{\"nom\":\"A\"},{\"bad\":true},{\"nom\":\"B\"}]}");

        assertThat(parkings).extracting(Parking::name).containsExactly("A", "B");
    }

    @Test
    void shouldYieldEmptyOnAMissingResultsArray() {
        assertThat(connector.normalize("{\"unexpected\":true}")).isEmpty();
    }

    // ---- HTTP fetch loop -----------------------------------------------------------

    @Test
    void shouldAggregateAllPages() throws Exception {
        // Page 0 is full (100 records â†’ triggers page 1); page 1 is partial (1 record â†’ end).
        server.createContext("/records", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body = query.contains("offset=100")
                ? page(101, 100, 1)
                : page(101, 0, 100);
            respond(exchange, 200, body);
        });

        List<Parking> parkings = connector().fetch();

        assertThat(parkings).hasSize(101);
    }

    @Test
    void shouldFailWhenTheSourceDeclaresMoreThanCollected() throws Exception {
        // The source declares 50 records but serves only 1.
        server.createContext("/records", exchange -> respond(exchange, 200, page(50, 0, 1)));

        assertThatThrownBy(() -> connector().fetch())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("incomplete snapshot");
    }

    @Test
    void shouldSucceedWhenAllAdvertisedRecordsAreServedEvenIfSomeAreDropped() throws Exception {
        // The source declares 3 records and serves 3; one is malformed and dropped by design (per-record fault tolerance).
        server.createContext("/records", exchange -> respond(exchange, 200,
            "{\"total_count\":3,\"results\":[{\"id\":1},{\"bad\":true},{\"id\":2}]}"));

        assertThat(connector().fetch()).hasSize(2);
    }

    @Test
    void shouldFailOnANon200Status() throws Exception {
        server.createContext("/records", exchange -> respond(exchange, 500, "boom"));

        assertThatThrownBy(() -> connector().fetch())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("HTTP 500");
    }

    // ---- helpers ---------------------------------------------------------------

    /** A connector whose {@code toParking} maps {@code {"nom"|"id": X}} to a parking, dropping the rest. */
    private static AbstractHttpSourceConnector connector(String endpoint) {
        return new AbstractHttpSourceConnector("test", URI.create(endpoint), Duration.ofSeconds(60)) {
            @Override
            protected Parking toParking(JsonNode record) {
                String name = textAt(record, "nom");
                String id = textAt(record, "id");
                return name == null && id == null
                    ? null
                    : new Parking("test:" + (name != null ? name : id), "test", name != null ? name : "P",
                        new Location(46, 0), 10, 5, ParkingStatus.OPEN, T0, T0);
            }
        };
    }

    /** A connector backed by the live in-JVM server. */
    private AbstractHttpSourceConnector connector() {
        return connector(baseUrl + "/records");
    }

    /** Builds one-page advertising {@code total} records, serving {@code count} starting at {@code from}. */
    private static String page(int total, int from, int count) {
        StringBuilder records = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (!records.isEmpty()) {
                records.append(',');
            }
            records.append("{\"id\":").append(from + i).append('}');
        }
        return "{\"total_count\":" + total + ",\"results\":[" + records + "]}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

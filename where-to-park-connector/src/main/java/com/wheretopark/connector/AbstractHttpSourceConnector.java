package com.wheretopark.connector;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Template-method base for HTTP connectors polling a JSON open-data API returning a {@code {"total_count|total": N, "results": [...]}} envelope.
 * Centralizes paged GET with timeout, gzip, per-record fault tolerance, a completeness check against the advertised total, and the field extractors.
 * Subclasses only declare the endpoint and map one record to a {@link Parking}.
 *
 * <p>
 * Completeness contract: a fetch retrieving fewer records than advertised is truncated and fails â€” serving the previous snapshot beats silently dropping parkings.
 */
@Slf4j
public abstract class AbstractHttpSourceConnector implements ParkingSourceConnector {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    // Records requested per page.
    private static final int PAGE_SIZE = 100;

    // Safety bound on the number of pages fetched, so a misbehaving source cannot loop forever.
    private static final int MAX_PAGES = 50;

    private final String cityId;
    private final URI endpoint;
    private final Duration pollInterval;
    private final HttpClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * Creates a connector for a source whose records endpoint is paged and returns a JSON envelope with a {@code results} array.
     * 
     * @param cityId the city identifier
     * @param endpoint the full request URL of the dataset records endpoint (without paging parameters)
     * @param pollInterval the minimum delay between two polls
     */
    protected AbstractHttpSourceConnector(String cityId, URI endpoint, Duration pollInterval) {
        this.cityId = cityId;
        this.endpoint = endpoint;
        this.pollInterval = pollInterval;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * Returns the identifier of the city served by this connector.
     *
     * @return the city identifier, e.g. {@code "poitiers"}
     */
    @Override
    public String cityId() {
        return cityId;
    }

    /**
     * Returns the minimum delay between two polls of this source.
     *
     * @return the poll interval
     */
    @Override
    public Duration pollInterval() {
        return pollInterval;
    }

    /**
     * Fetches all pages and normalizes them into the current snapshot of the source.
     *
     * @return the full snapshot for this city (replace semantics, not a delta)
     * @throws IOException on network/HTTP failure, or when the source serves fewer records than it declares
     * @throws InterruptedException when the request is interrupted
     */
    @Override
    public List<Parking> fetch() throws IOException, InterruptedException {
        List<Parking> parkings = new ArrayList<>();
        Instant fetchedAt = Instant.now();

        long declaredTotal = -1;
        int page = 0;
        int served = 0;
        boolean more = true;

        while (more && page < MAX_PAGES) {
            JsonNode root = mapper.readTree(get(pageUri(page)));
            declaredTotal = declaredTotal(root);
            JsonNode results = root.path("results");
            int servedOnPage = results.isArray() ? results.size() : 0;
            served += servedOnPage;
            int before = parkings.size();
            collect(results, parkings, fetchedAt);
            // Stop after a partial page (end of data) or when nothing new was collected.
            more = servedOnPage == PAGE_SIZE && parkings.size() > before;
            page++;
        }

        if (page == MAX_PAGES) {
            log.warn("[{}] reached the {}-page safety bound, snapshot may be truncated", cityId, MAX_PAGES);
        }

        // Completeness is checked against records served by the source, not records kept: unusable records are already dropped with a warning by collect().
        if (declaredTotal > served) {
            throw new IOException("incomplete snapshot for " + cityId + ": source declares " + declaredTotal + " records but only " + served + " were served");
        }

        return parkings;
    }

    /**
     * Normalizes a raw JSON payload into canonical parkings, stamping each with the current fetch time. Public to let the connector contract tests exercise it against golden payloads.
     *
     * @param body the raw JSON payload
     * @return the normalized parking list
     */
    public List<Parking> normalize(String body) {
        JsonNode root = mapper.readTree(body);
        List<Parking> parkings = new ArrayList<>();
        collect(root.path("results"), parkings, Instant.now());
        return parkings;
    }

    /**
     * Maps one record of the {@code results} array to the canonical model.
     *
     * @param record one element of {@code results}
     * @return the parking, or {@code null} when the record is unusable (it is then dropped with a warning)
     */
    protected abstract Parking toParking(JsonNode record);

    /**
     * Builds the URI of one page. The default is an {@code offset}/{@code limit} query pair; a source with different paging semantics overrides this method.
     *
     * @param page the zero-based page index
     * @return the page request URI
     */
    protected URI pageUri(int page) {
        String separator = endpoint.getRawQuery() == null ? "?" : "&";
        return URI.create(endpoint + separator + "limit=" + PAGE_SIZE + "&offset=" + (page * PAGE_SIZE));
    }

    /**
     * Reads the source-advertised total record count (for the completeness check). The default accepts the two common field names.
     *
     * @param root the page payload root
     * @return the advertised total, or {@code -1} when the source reports none
     */
    protected static long declaredTotal(JsonNode root) {
        JsonNode total = root.get("total_count") != null ? root.get("total_count") : root.get("total");
        return total != null && total.isNumber() ? total.longValue() : -1;
    }

    // ---- field extraction helpers -------------------------------------------------

    /**
     * Reads a text field from a node; the path is a flat field name or a dot-separated path (e.g. {@code "grp_nom"} or {@code "location.lat"}).
     *
     * @param node the node to read from
     * @param fieldPath the field path
     * @return the text, or {@code null} when blank or missing
     */
    protected static String textAt(JsonNode node, String fieldPath) {
        if (fieldPath == null) {
            return null;
        }

        JsonNode value = navigate(node, fieldPath);
        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asString();
        return text == null || text.isBlank() ? null : text;
    }

    /**
     * Reads an integer field from a node.
     *
     * @param node the node to read from
     * @param fieldPath the field path
     * @return the integer, or {@code null} when non-numeric or missing
     */
    protected static Integer intAt(JsonNode node, String fieldPath) {
        if (fieldPath == null) {
            return null;
        }

        JsonNode value = navigate(node, fieldPath);
        return value == null || !value.isNumber() ? null : value.intValue();
    }

    /**
     * Reads a decimal field from a node.
     *
     * @param node the node to read from
     * @param fieldPath the field path
     * @return the decimal, or {@code null} when non-numeric or missing
     */
    protected static Double doubleAt(JsonNode node, String fieldPath) {
        if (fieldPath == null) {
            return null;
        }

        JsonNode value = navigate(node, fieldPath);
        return value == null || !value.isNumber() ? null : value.doubleValue();
    }

    /**
     * Parses a latitude/longitude text pair into a {@link Location}.
     *
     * @param latText the latitude text
     * @param lonText the longitude text
     * @return the location, or {@code null} when either is missing, blank or non-numeric
     */
    protected static Location locationFrom(String latText, String lonText) {
        if (latText == null || latText.isBlank() || lonText == null || lonText.isBlank()) {
            return null;
        }

        try {
            return new Location(Double.parseDouble(latText.trim()), Double.parseDouble(lonText.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Maps each record of the {@code results} array to the canonical model, stamping each with the fetch time. Unusable records are dropped with a warning.
     *
     * @param results the {@code results} array node
     * @param parkings the accumulator collecting the normalized parkings
     * @param fetchedAt the fetch time to stamp
     */
    private void collect(JsonNode results, List<Parking> parkings, Instant fetchedAt) {
        if (!results.isArray()) {
            log.warn("[{}] payload carries no results array", cityId);
            return;
        }

        for (JsonNode record : results) {
            try {
                Parking parking = toParking(record);
                if (parking != null) {
                    parkings.add(stampFetchedAt(parking, fetchedAt));
                }
            } catch (RuntimeException e) {
                log.warn("[{}] dropping malformed record: {}", cityId, e.toString());
            }
        }
    }

    /**
     * Executes one GET and returns the decoded body.
     *
     * @param uri the request URI
     * @return the response body as text
     * @throws IOException on non-200 status or decompression failure
     * @throws InterruptedException when the request is interrupted
     */
    private String get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "gzip")
            .GET()
            .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("source answered HTTP " + response.statusCode() + " for " + uri);
        }

        return decompressIfNeeded(response);
    }

    /**
     * Stamps a parking with the fetch time (records built by connectors carry no fetchedAt).
     *
     * @param parking the parking as built by the connector
     * @param fetchedAt the fetch time
     * @return a copy of the parking carrying {@code fetchedAt}
     */
    private static Parking stampFetchedAt(Parking parking, Instant fetchedAt) {
        return new Parking(parking.id(), parking.cityId(), parking.name(), parking.location(), parking.capacity(), parking.availableSpots(), parking.status(), parking.sourceUpdatedAt(), fetchedAt);
    }

    /**
     * Decompresses the response body if the server sent GZIP-encoded content.
     *
     * @param response the HTTP response
     * @return the decoded body text
     * @throws IOException on decompression failure
     */
    private static String decompressIfNeeded(HttpResponse<byte[]> response) throws IOException {
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        byte[] body = response.body();

        boolean declaredGzip = "gzip".equalsIgnoreCase(encoding);

        // GZIP magic bytes (RFC 1952): every gzip stream starts with 0x1f 0x8b. The server sometimes omits the Content-Encoding header but still compresses, so sniff them.
        boolean gzipMagic = body.length > 2 && body[0] == (byte) 0x1f && body[1] == (byte) 0x8b;

        if (declaredGzip || gzipMagic) {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Navigates a dot-separated field path in a JSON node, returning the leaf node or {@code null} when any part is missing.
     * 
     * @param node the root node
     * @param fieldPath the dot-separated field path (e.g. {@code "location.lat"})
     * @return the leaf node, or {@code null} when any part is missing
     */
    private static JsonNode navigate(JsonNode node, String fieldPath) {
        JsonNode current = node;

        for (String part : fieldPath.split("\\.")) {
            if (current == null || current.isNull()) {
                return null;
            }

            current = current.get(part);
        }

        return current;
    }
}

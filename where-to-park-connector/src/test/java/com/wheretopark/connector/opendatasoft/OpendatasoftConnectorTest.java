package com.wheretopark.connector.opendatasoft;

import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests of {@link OpendatasoftConnector} against a recorded golden payload.
 *
 * <p>
 * The payload mirrors the live Nantes feed as observed on 2026-10-01, including a record without coordinates (must be dropped) and a full parking (0 places, status 5).
 */
class OpendatasoftConnectorTest {

    // The Nantes grp_statut convention, declared explicitly like any other city (5=open, 1=closed, 2=subscribers only; 0 invalid â†’ absent â†’ UNKNOWN).
    private static final OpendatasoftConnector.FieldMapping NANTES_MAPPING = new OpendatasoftConnector.FieldMapping(
        "grp_identifiant", "grp_nom",
        "location.lat", "location.lon",
        "grp_exploitation", "grp_disponible",
        "grp_statut", "grp_horodatage",
        Map.of(5, "OPEN_OR_FULL", 1, "CLOSED", 2, "CLOSED"));

    private final OpendatasoftConnector connector = new OpendatasoftConnector("nantes",
        "https://data.nantesmetropole.fr",
        "unused-in-tests",
        Duration.ofSeconds(60),
        NANTES_MAPPING);

    @Test
    void shouldNormalizeTheGoldenPayload() {
        List<Parking> parkings = connector.normalize(goldenPayload());

        assertThat(parkings).hasSize(2);
        assertThat(parkings).allSatisfy(p -> {
            assertThat(p.id()).startsWith("nantes:");
            assertThat(p.cityId()).isEqualTo("nantes");
            assertThat(p.sourceUpdatedAt()).isNotNull();
        });
    }

    @Test
    void shouldMapFieldsToTheCanonicalModel() {
        Parking decre = byName(connector.normalize(goldenPayload()), "DECRE - BOUFFAY");

        assertThat(decre.id()).isEqualTo("nantes:2");
        assertThat(decre.location().lat()).isEqualTo(47.21383753673762);
        assertThat(decre.location().lon()).isEqualTo(-1.5536062121547323);
        assertThat(decre.capacity()).isEqualTo(394);
        assertThat(decre.availableSpots()).isEqualTo(222);
        assertThat(decre.status()).isEqualTo(ParkingStatus.OPEN);
        assertThat(decre.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-10-01T14:38:41Z"));
    }

    @Test
    void shouldReportFullForStatus5WithZeroPlaces() {
        Parking aristide = byName(connector.normalize(goldenPayload()), "ARISTIDE BRIAND");

        assertThat(aristide.availableSpots()).isZero();
        assertThat(aristide.status()).isEqualTo(ParkingStatus.FULL);
    }

    @Test
    void shouldDropRecordsWithoutCoordinates() {
        List<Parking> parkings = connector.normalize(goldenPayload());

        assertThat(parkings).noneMatch(p -> p.name().equals("NO_COORDS"));
    }

    @Test
    void shouldReportClosedForStatus1() {
        String payload = """
                {"results":[{"grp_identifiant":9,"grp_nom":"CLOSED_ONE","grp_statut":1,"grp_disponible":50,
                "grp_exploitation":100,"grp_horodatage":"2026-10-01T14:38:41+00:00",
                "location":{"lon":-1.55,"lat":47.21}}]}
                """;

        Parking parking = connector.normalize(payload).get(0);

        assertThat(parking.status()).isEqualTo(ParkingStatus.CLOSED);
    }

    @Test
    void shouldReportUnknownForAMisconfiguredStatusMappingValue() {
        OpendatasoftConnector.FieldMapping badMapping = new OpendatasoftConnector.FieldMapping(
            "grp_identifiant", "grp_nom", "location.lat", "location.lon",
            "grp_exploitation", "grp_disponible", "grp_statut", "grp_horodatage",
            Map.of(5, "NOT_A_STATUS"));
        OpendatasoftConnector badConnector = new OpendatasoftConnector("nantes",
            "https://data.nantesmetropole.fr",
            "unused-in-tests",
            Duration.ofSeconds(60),
            badMapping);
        String payload = """
                {"results":[{"grp_identifiant":9,"grp_nom":"BAD","grp_statut":5,"grp_disponible":50,
                "grp_exploitation":100,"grp_horodatage":"2026-10-01T14:38:41+00:00",
                "location":{"lon":-1.55,"lat":47.21}}]}
                """;

        Parking parking = badConnector.normalize(payload).get(0);

        assertThat(parking.status()).isEqualTo(ParkingStatus.UNKNOWN);
    }

    @Test
    void shouldParseAnOffsetBearingTimestamp() {
        String payload = """
                {"results":[{"grp_identifiant":9,"grp_nom":"OFFSET","grp_statut":5,"grp_disponible":50,
                "grp_exploitation":100,"grp_horodatage":"2026-10-01T14:38:41+02:00",
                "location":{"lon":-1.55,"lat":47.21}}]}
                """;

        Parking parking = connector.normalize(payload).get(0);

        assertThat(parking.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-10-01T12:38:41Z"));
    }

    @Test
    void shouldYieldUnknownAndFetchTimeForAStaticDataset() {
        OpendatasoftConnector.FieldMapping staticMapping = new OpendatasoftConnector.FieldMapping("id", "nom", "geo_point_2d.lat", "geo_point_2d.lon", "nb_places", null, null, null, null);
        OpendatasoftConnector staticConnector = new OpendatasoftConnector("toulouse", "https://data.toulouse-metropole.fr", "unused-in-tests", Duration.ofSeconds(300), staticMapping);
        String payload = """
                {"results":[{"id":1,"nom":"CAPITOLE","nb_places":500,"geo_point_2d":{"lon":1.44,"lat":43.60}}]}
                """;

        Instant before = Instant.now();
        Parking parking = staticConnector.normalize(payload).get(0);
        Instant after = Instant.now();

        assertThat(parking.status()).isEqualTo(ParkingStatus.UNKNOWN);
        assertThat(parking.availableSpots()).isNull();
        // No timestamp field: falls back to the fetch time.
        assertThat(parking.sourceUpdatedAt()).isBetween(before, after);
    }

    private static Parking byName(List<Parking> parkings, String name) {
        return parkings.stream()
            .filter(p -> p.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no parking named " + name));
    }

    private static String goldenPayload() {
        try (InputStream in = OpendatasoftConnectorTest.class.getResourceAsStream("/opendatasoft-nantes-sample.json")) {
            if (in == null) {
                throw new IllegalStateException("golden payload not found on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

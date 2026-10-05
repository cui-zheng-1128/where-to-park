package com.wheretopark.connector.datafair;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests of {@link DataFairConnector} against a recorded golden payload.
 *
 * <p>
 * The payload mirrors the live Grand Poitiers feed as observed on 2026-09-28, including its quirks: a parking without coordinates (GARE EFFIA) and a full one (THEATRE, 0 places,
 * 100% occupancy). If the source renames or reshapes fields, this test breaks before users notice.
 */
class DataFairConnectorTest {

    private final DataFairConnector connector = new DataFairConnector("poitiers", "https://data.grandpoitiers.fr", "unused-in-tests", Duration.ofSeconds(60));

    @Test
    void shouldNormalizeTheGoldenPayload() {
        List<Parking> parkings = connector.normalize(goldenPayload());

        assertThat(parkings).hasSize(3);
        assertThat(parkings).allSatisfy(p -> {
            assertThat(p.id()).startsWith("poitiers:");
            assertThat(p.cityId()).isEqualTo("poitiers");
            assertThat(p.sourceUpdatedAt()).isNotNull();
        });
    }

    @Test
    void shouldMapFieldsToTheCanonicalModel() {
        Parking notreDame = byName(connector.normalize(goldenPayload()), "NOTRE DAME");

        assertThat(notreDame.id()).isEqualTo("poitiers:1");
        assertThat(notreDame.location().lat()).isEqualTo(46.58349874703973);
        assertThat(notreDame.location().lon()).isEqualTo(0.3450022616476489);
        assertThat(notreDame.capacity()).isEqualTo(146);
        assertThat(notreDame.availableSpots()).isEqualTo(53);
        assertThat(notreDame.status()).isEqualTo(ParkingStatus.OPEN);
        assertThat(notreDame.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-09-28T18:37:32.000Z"));
    }

    @Test
    void shouldReportFullAtZeroPlacesWithFullOccupancy() {
        Parking theatre = byName(connector.normalize(goldenPayload()), "THEATRE");

        assertThat(theatre.availableSpots()).isZero();
        assertThat(theatre.status()).isEqualTo(ParkingStatus.FULL);
    }

    @Test
    void shouldReportUnknownAtZeroPlacesWithoutOccupancyRate() {
        Parking palais = byName(connector.normalize(goldenPayload()), "PALAIS DE JUSTICE");

        assertThat(palais.availableSpots()).isZero();
        assertThat(palais.status()).isEqualTo(ParkingStatus.UNKNOWN);
    }

    @Test
    void shouldDropRecordsWithoutCoordinates() {
        List<Parking> parkings = connector.normalize(goldenPayload());

        assertThat(parkings).noneMatch(p -> p.name().equals("GARE EFFIA"));
    }

    @Test
    void shouldDropRecordsWithoutTimestamp() {
        String broken = """
                {"total":1,"results":[{"Id":9,"Nom":"BROKEN","Capacite":10,"Places":4,
                "_geopoint":"46.58, 0.34"}]}
                """;

        assertThat(connector.normalize(broken)).isEmpty();
    }

    @Test
    void shouldDropRecordsWithMalformedGeopoint() {
        String broken = """
                {"total":2,"results":[
                  {"Id":1,"Nom":"NO_COMMA","_geopoint":"46.58","Dernière_mise_à_jour_Base":"2026-09-28T18:37:32.000Z"},
                  {"Id":2,"Nom":"NOT_NUMERIC","_geopoint":"abc, def","Dernière_mise_à_jour_Base":"2026-09-28T18:37:32.000Z"}]}
                """;

        assertThat(connector.normalize(broken)).isEmpty();
    }

    @Test
    void shouldReportUnknownWhenPlacesIsNull() {
        String payload = """
                {"total":1,"results":[{"Id":1,"Nom":"NO_PLACES","Capacite":10,
                "_geopoint":"46.58, 0.34","Dernière_mise_à_jour_Base":"2026-09-28T18:37:32.000Z"}]}
                """;

        Parking parking = connector.normalize(payload).get(0);

        assertThat(parking.availableSpots()).isNull();
        assertThat(parking.status()).isEqualTo(ParkingStatus.UNKNOWN);
    }

    private static Parking byName(List<Parking> parkings, String name) {
        return parkings.stream()
            .filter(p -> p.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no parking named " + name));
    }

    private static String goldenPayload() {
        try (InputStream in = DataFairConnectorTest.class.getResourceAsStream("/datafair-lines-sample.json")) {
            if (in == null) {
                throw new IllegalStateException("golden payload not found on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

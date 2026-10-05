package com.wheretopark.mapper;

import com.wheretopark.dto.ParkingDTO;
import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;
import com.wheretopark.service.model.ScoredParking;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link ParkingMapper}. Uses the MapStruct-generated {@code ParkingMapperImpl}.
 */
class ParkingMapperTest {

    private final ParkingMapper mapper = new ParkingMapperImpl();

    @Test
    void mapsScoredParkingToDto() {
        Parking parking = new Parking("poitiers:1", "poitiers", "NOTRE DAME",
            new Location(46.583, 0.345), 146, 53, ParkingStatus.OPEN,
            Instant.parse("2026-09-28T18:37:32Z"), Instant.parse("2026-09-28T18:38:00Z"));

        ParkingDTO dto = mapper.toDTO(new ScoredParking(parking, 250));

        assertThat(dto.getId()).isEqualTo("poitiers:1");
        assertThat(dto.getCityId()).isEqualTo("poitiers");
        assertThat(dto.getName()).isEqualTo("NOTRE DAME");
        assertThat(dto.getLocation()).isEqualTo(new Location(46.583, 0.345));
        assertThat(dto.getCapacity()).isEqualTo(146);
        assertThat(dto.getAvailableSpots()).isEqualTo(53);
        assertThat(dto.getStatus()).isEqualTo(ParkingStatus.OPEN);
        assertThat(dto.getSourceUpdatedAt()).isEqualTo(Instant.parse("2026-09-28T18:37:32Z"));
        assertThat(dto.getDistanceMeters()).isEqualTo(250);
        // driveSeconds stays null until a routing engine exists.
        assertThat(dto.getDriveSeconds()).isNull();
    }
}

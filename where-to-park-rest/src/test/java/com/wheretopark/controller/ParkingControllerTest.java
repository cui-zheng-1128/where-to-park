package com.wheretopark.controller;

import com.wheretopark.configuration.ParkingIngestionProperties;
import com.wheretopark.mapper.ParkingMapper;
import com.wheretopark.model.Location;
import com.wheretopark.model.Parking;
import com.wheretopark.model.ParkingStatus;
import com.wheretopark.service.ParkingService;
import com.wheretopark.service.model.Ranking;
import com.wheretopark.service.model.ScoredParking;
import com.wheretopark.service.model.SearchResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests of {@link ParkingController}. Only the controller slice is loaded; the service is mocked, the real MapStruct mapper and ingestion properties are imported.
 */
@WebMvcTest(ParkingController.class)
@Import({ParkingIngestionProperties.class, ParkingControllerTest.MapperConfig.class})
class ParkingControllerTest {

    // ETag is a hash of every served field of every result (see ParkingController#dataVersion).
    private static final String ETAG = "\"9bca9440\"";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParkingIngestionProperties properties;

    @MockitoBean
    private ParkingService parkingService;

    @BeforeEach
    void setUp() {
        properties.setSources(Map.of("poitiers", source("poitiers")));
        when(parkingService.findNearby(any(Location.class), anyInt(), anyInt(), any(Ranking.class)))
            .thenReturn(new SearchResult(List.of(scoredParking()), Ranking.DISTANCE));
    }

    @Test
    void nearbyReturnsResultsWithAttributionAndEtag() throws Exception {
        mockMvc.perform(get("/v1/parkings/nearby").param("lat", "46.58").param("lon", "0.34"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(jsonPath("$.rankingRequested").value("DISTANCE"))
            .andExpect(jsonPath("$.rankingApplied").value("DISTANCE"))
            .andExpect(jsonPath("$.results[0].id").value("poitiers:1"))
            .andExpect(jsonPath("$.attribution[0].cityId").value("poitiers"))
            .andExpect(jsonPath("$.attribution[0].source").value("Grand Poitiers - open data"));
    }

    @Test
    void returns304WhenIfNoneMatchMatchesTheDataVersion() throws Exception {
        mockMvc.perform(get("/v1/parkings/nearby")
            .param("lat", "46.58")
            .param("lon", "0.34")
            .header("If-None-Match", ETAG))
            .andExpect(status().isNotModified())
            .andExpect(header().string("ETag", ETAG));
    }

    @Test
    void outOfRangeLatitudeYields400() throws Exception {
        mockMvc.perform(get("/v1/parkings/nearby").param("lat", "146.5").param("lon", "0.34"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("BAD_REQUEST"));
    }

    @Test
    void missingLatitudeYields400() throws Exception {
        mockMvc.perform(get("/v1/parkings/nearby").param("lon", "0.34"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericLatitudeYields400() throws Exception {
        mockMvc.perform(get("/v1/parkings/nearby").param("lat", "abc").param("lon", "0.34"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("lat: invalid value: abc"));
    }

    @Test
    void lowerCaseRankingIsAccepted() throws Exception {
        mockMvc.perform(get("/v1/parkings/nearby")
            .param("lat", "46.58")
            .param("lon", "0.34")
            .param("ranking", "distance"))
            .andExpect(status().isOk());
    }

    @Test
    void invalidRankingYields400() throws Exception {
        mockMvc.perform(get("/v1/parkings/nearby")
            .param("lat", "46.58")
            .param("lon", "0.34")
            .param("ranking", "FOO"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postIsNotMappedAndYields405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/v1/parkings/nearby"))
            .andExpect(status().isMethodNotAllowed());
    }

    private static ScoredParking scoredParking() {
        Parking parking = new Parking("poitiers:1", "poitiers", "NOTRE DAME",
            new Location(46.583, 0.345), 146, 53, ParkingStatus.OPEN,
            Instant.parse("2026-09-28T18:37:33Z"), Instant.parse("2026-09-28T18:37:33Z"));
        return new ScoredParking(parking, 250);
    }

    private static ParkingIngestionProperties.Source source(String cityId) {
        ParkingIngestionProperties.Attribution attribution = new ParkingIngestionProperties.Attribution();
        attribution.setSource("Grand Poitiers - open data");
        attribution.setLicense("Licence Ouverte / Open Licence 2.0");
        attribution.setUrl("https://data.grandpoitiers.fr/datasets/x");
        ParkingIngestionProperties.Source source = new ParkingIngestionProperties.Source();
        source.setAttribution(attribution);
        return source;
    }

    // The MapStruct-generated mapper is not a component-scanned bean in a @WebMvcTest slice.
    @TestConfiguration
    protected static class MapperConfig {
        @Bean
        ParkingMapper parkingMapper() {
            return new com.wheretopark.mapper.ParkingMapperImpl();
        }
    }
}

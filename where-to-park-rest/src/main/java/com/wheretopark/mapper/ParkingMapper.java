package com.wheretopark.mapper;

import com.wheretopark.dto.ParkingDTO;
import com.wheretopark.service.model.ScoredParking;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps scored parking model to its v1 JSON representation.
 */
@Mapper(componentModel = "spring")
public interface ParkingMapper {

    /**
     * Converts a scored parking into its DTO, copying the domain fields and exposing the computed distance. {@code driveSeconds} stays {@code null} until a routing engine exists.
     *
     * @param scoredParking the parking with its distance from the origin
     * @return the DTO to serialize
     */
    @Mapping(target = "id", source = "parking.id")
    @Mapping(target = "cityId", source = "parking.cityId")
    @Mapping(target = "name", source = "parking.name")
    @Mapping(target = "location", source = "parking.location")
    @Mapping(target = "capacity", source = "parking.capacity")
    @Mapping(target = "availableSpots", source = "parking.availableSpots")
    @Mapping(target = "status", source = "parking.status")
    @Mapping(target = "sourceUpdatedAt", source = "parking.sourceUpdatedAt")
    @Mapping(target = "fetchedAt", source = "parking.fetchedAt")
    @Mapping(target = "driveSeconds", ignore = true)
    ParkingDTO toDTO(ScoredParking scoredParking);
}

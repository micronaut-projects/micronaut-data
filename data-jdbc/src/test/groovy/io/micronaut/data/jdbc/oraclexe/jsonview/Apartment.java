package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_APARTMENT", alias = "ap")
public record Apartment(
    @EmbeddedId
    ApartmentId apartmentId
) {}

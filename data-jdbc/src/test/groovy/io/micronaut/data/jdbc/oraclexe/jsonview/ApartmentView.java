package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.JsonView;

@JsonView(entity = Apartment.class, alias = "aw")
public record ApartmentView(
    @EmbeddedId
    ApartmentId apartmentId
) {}

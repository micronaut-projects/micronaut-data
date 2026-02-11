package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubView(entity = Apartment.class, alias = "asw")
public record ApartmentSubView(
    @EmbeddedId
    @JsonUnwrapped
    ApartmentId apartmentId
) {}

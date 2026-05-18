package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Embeddable;

@Embeddable
@Serdeable
public record ApartmentId (
    Long buildingId,
    Long flatId
) {}

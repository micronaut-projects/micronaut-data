package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_CAR_DETAILS", alias = "cd")
public record CarDetails (
    @Id
    @GeneratedValue
    Long id,
    String model,
    Long year
) {}

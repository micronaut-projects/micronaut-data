package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.serde.annotation.Serdeable;

@Embeddable
@Serdeable
public record AirplaneDetails (
    String model,
    Long year
) {}

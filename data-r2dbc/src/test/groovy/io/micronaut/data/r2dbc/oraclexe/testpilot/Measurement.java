package io.micronaut.data.r2dbc.oraclexe.testpilot;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("TP_MEASUREMENT")
public record Measurement(@Id Long id, String label) {
}

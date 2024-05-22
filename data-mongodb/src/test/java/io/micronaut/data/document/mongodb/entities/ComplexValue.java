package io.micronaut.data.document.mongodb.entities;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ComplexValue (
    String valueA,
    String valueB) {
}

package io.micronaut.data.graph.tck.entities;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MovieSummary(String title, Integer released) {
}

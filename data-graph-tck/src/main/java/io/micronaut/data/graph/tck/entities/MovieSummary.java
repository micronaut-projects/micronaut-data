package io.micronaut.data.graph.tck.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record MovieSummary(String title, Integer released) {
}

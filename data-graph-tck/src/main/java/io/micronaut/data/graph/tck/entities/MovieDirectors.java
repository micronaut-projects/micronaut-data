package io.micronaut.data.graph.tck.entities;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record MovieDirectors(String title, List<String> directors) {
}

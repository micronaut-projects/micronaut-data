package io.micronaut.data.graph.model.query.builder.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node
public record Company(@Id String name) {
}

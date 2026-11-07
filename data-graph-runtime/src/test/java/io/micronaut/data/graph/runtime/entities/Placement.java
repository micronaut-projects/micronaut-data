package io.micronaut.data.graph.runtime.entities;

import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;
import io.micronaut.serde.annotation.Serdeable;

@RelationshipProperties
@Serdeable
public record Placement(Integer position, @TargetNode Item item) {
}

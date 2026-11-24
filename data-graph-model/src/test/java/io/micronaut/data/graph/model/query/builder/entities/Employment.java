package io.micronaut.data.graph.model.query.builder.entities;

import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

@RelationshipProperties
public record Employment(@RelationshipId Long id, @TargetNode Company company) {
}

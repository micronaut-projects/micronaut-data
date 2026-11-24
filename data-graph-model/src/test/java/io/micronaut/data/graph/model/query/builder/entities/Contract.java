package io.micronaut.data.graph.model.query.builder.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

@RelationshipProperties
public record Contract(@Id String number, @TargetNode Company company) {
}

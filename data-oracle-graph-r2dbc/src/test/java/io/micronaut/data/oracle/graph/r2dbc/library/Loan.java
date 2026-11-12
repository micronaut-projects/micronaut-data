package io.micronaut.data.oracle.graph.r2dbc.library;

import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

@RelationshipProperties
public record Loan(@RelationshipId Long id, Integer days, @TargetNode Book book) {
}

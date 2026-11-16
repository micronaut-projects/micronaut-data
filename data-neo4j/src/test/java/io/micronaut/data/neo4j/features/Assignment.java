package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

@RelationshipProperties
public record Assignment(@RelationshipId Long id, String title, @TargetNode Project project) {
}

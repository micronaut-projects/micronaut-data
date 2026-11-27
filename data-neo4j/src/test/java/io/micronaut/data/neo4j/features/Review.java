package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

@RelationshipProperties
public record Review(@Id String id, String verdict, @TargetNode Project project) {
}

package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

/**
 * A relationship entity without an identifier, which is optional.
 */
@RelationshipProperties
public record Seat(Integer chair, @TargetNode Project project) {
}

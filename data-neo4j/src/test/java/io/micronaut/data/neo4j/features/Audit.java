package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

import java.util.UUID;

@RelationshipProperties
public record Audit(@Id @AutoPopulated UUID id, String note, @TargetNode Project project) {
}

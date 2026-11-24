package io.micronaut.data.neo4j.features;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Names its properties for JSON with a strategy looked up at runtime, while the node keeps the Java names.
 */
@Node
@Serdeable(naming = PrefixNamingStrategy.class)
public record Reviewer(@Id String fullName, Integer reviewCount, @Nullable @Relationship("TRAINED") List<Reviewer> trainees) {
}

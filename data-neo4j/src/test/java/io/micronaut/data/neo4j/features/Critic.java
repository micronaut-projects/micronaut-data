package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

/**
 * Names its properties for JSON with a serde naming strategy, while the node keeps the Java names.
 */
@Node
@Serdeable(naming = SnakeCaseStrategy.class)
public record Critic(@Id String fullName, Integer reviewCount) {
}

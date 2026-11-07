package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node
public record Project(@Id String name) {
}

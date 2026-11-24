package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

/**
 * An entity whose nodes are those of {@link Guest} too: they carry both labels.
 */
@Node(labels = {"Host", "Guest"})
public record Host(@Id String name) {
}

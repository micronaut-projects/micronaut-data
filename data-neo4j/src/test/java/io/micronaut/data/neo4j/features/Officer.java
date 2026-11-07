package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

/**
 * Another entity over the nodes of {@link Member}: its nodes carry both labels.
 */
@Node(labels = {"Officer", "Member"})
public record Officer(@Id @GeneratedValue String id, String name) {
}

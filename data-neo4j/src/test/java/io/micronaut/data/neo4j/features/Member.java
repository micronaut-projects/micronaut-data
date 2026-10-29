package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import org.jspecify.annotations.Nullable;

@Node
public record Member(@Id @GeneratedValue String id, String name, @Relationship("MENTORED_BY") @Nullable Member mentor) {
}

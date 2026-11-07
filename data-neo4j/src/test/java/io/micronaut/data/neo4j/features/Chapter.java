package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import org.jspecify.annotations.Nullable;

@Node
public record Chapter(@Id @GeneratedValue @Nullable String id, String name, @Version @Nullable Long version, @Relationship("NEXT") @Nullable Chapter next) {
}

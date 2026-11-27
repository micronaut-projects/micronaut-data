package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import org.jspecify.annotations.Nullable;

/**
 * An immutable entity with an assigned identifier, which the entity events replace to populate its version.
 */
@Node
public record Milestone(@Id String name, @Version @Nullable Long version, @Relationship("NEXT") @Nullable Milestone next) {
}

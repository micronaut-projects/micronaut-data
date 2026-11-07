package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * An immutable entity, which the entity events replace to populate its identifier.
 */
@Node
public record Step(@Id @AutoPopulated @Nullable UUID id, String name, @Relationship("NEXT") @Nullable Step next) {
}

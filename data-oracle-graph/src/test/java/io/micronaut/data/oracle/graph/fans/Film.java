package io.micronaut.data.oracle.graph.fans;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node(value = "films", labels = "Film")
public record Film(@Id Long id,
                   String title,
                   @Relationship(type = "LIKES", direction = Direction.INCOMING) List<Fan> fans) {
}

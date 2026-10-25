package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node
public record Team(@Id @GeneratedValue String id,
                   String name,
                   @Relationship(type = "PLAYS_FOR", direction = Direction.INCOMING) List<Player> players) {
}

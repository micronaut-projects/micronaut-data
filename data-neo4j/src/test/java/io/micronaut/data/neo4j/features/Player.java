package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

@Node
public record Player(@Id @GeneratedValue String id,
                     String name,
                     @Relationship("PLAYS_FOR") Team team) {
}

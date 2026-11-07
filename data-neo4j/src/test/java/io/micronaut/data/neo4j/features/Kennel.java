package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node
public record Kennel(@Id String name, @Relationship("HOUSES") List<Pet> pets) {
}

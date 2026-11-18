package io.micronaut.data.age.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node
public record Ensemble(@Id String name, @Relationship("SEATS") List<Seat> seats) {
}

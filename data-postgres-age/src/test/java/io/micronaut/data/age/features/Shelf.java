package io.micronaut.data.age.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node
public record Shelf(@Id @GeneratedValue Long id,
                    String name,
                    @Relationship(type = "HOLDS") List<Volume> volumes) {
}

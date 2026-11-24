package io.micronaut.data.oracle.graph.schemas;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node(value = "items", labels = "Shelf")
@MappedEntity(value = "items", schema = "LIBRARY")
public record Shelf(@Id Long id, @Relationship("HOLDS") List<Item> items) {
}

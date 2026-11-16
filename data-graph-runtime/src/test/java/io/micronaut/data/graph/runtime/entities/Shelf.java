package io.micronaut.data.graph.runtime.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Set;

@Node
@Serdeable
public record Shelf(@Id String name,
                    @Relationship("HOLDS") List<Item> items,
                    @Relationship("PLACES") List<Placement> placements,
                    @Relationship("TAGGED") Set<Item> tags,
                    @Relationship("NEXT_TO") Shelf neighbour) {
}

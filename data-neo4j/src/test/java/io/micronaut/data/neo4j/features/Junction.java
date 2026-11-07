package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node
public record Junction(@Id String name,
                       @Relationship(type = "LINKS") List<Junction> links,
                       @Relationship(type = "NEXT") List<Junction> node) {
}

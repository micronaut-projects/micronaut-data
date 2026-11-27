package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Node
public record Note(@Id String name, @Nullable List<String> tags) {
}

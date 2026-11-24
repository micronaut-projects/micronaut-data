package io.micronaut.data.oracle.graph.schemas;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node(value = "items", labels = "Item")
public record Item(@Id Long id) {
}

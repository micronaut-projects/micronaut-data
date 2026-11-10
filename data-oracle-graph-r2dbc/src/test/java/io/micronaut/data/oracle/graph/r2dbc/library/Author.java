package io.micronaut.data.oracle.graph.r2dbc.library;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node(value = "authors", labels = "Author")
public record Author(@Id Long id, String name) {
}

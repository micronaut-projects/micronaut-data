package io.micronaut.data.oracle.graph.r2dbc.library;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node(value = "books", labels = "Book")
public record Book(@Id Long id,
                   String title,
                   @Relationship(type = "WROTE", direction = Direction.INCOMING) List<Author> authors) {
}

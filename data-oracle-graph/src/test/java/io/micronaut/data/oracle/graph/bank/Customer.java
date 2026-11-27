package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;
import org.jspecify.annotations.Nullable;

/**
 * A node with a single relationship, which the graph stores in an edge table.
 */
@Node(value = "customers", labels = "Customer")
public record Customer(@Id Long id, String name, @Nullable @Relationship("REFERRED_BY") Customer referrer) {
}

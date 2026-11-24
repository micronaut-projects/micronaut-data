package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

/**
 * An account seen from its transfers, whether it makes or receives them.
 */
@Node("Account")
public record Trader(@Id Long id, String name,
                     @Relationship(type = "TRANSFERS", direction = Direction.UNDIRECTED) List<Exchange> exchanged) {
}

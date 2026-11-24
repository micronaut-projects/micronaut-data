package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

/**
 * An account seen from the transfers it receives.
 */
@Node("Account")
public record Payee(@Id Long id, String name,
                    @Relationship(type = "TRANSFERS", direction = Direction.INCOMING) List<Receipt> received,
                    @Relationship(type = "TRANSFERS", direction = Direction.UNDIRECTED) List<Exchange> exchanged) {
}

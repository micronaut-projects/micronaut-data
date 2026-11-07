package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

/**
 * An account of the bank graph whose transfers are read in either direction.
 */
@Node("Account")
public record Peer(@Id Long id, String name, @Relationship(value = "TRANSFERS", direction = Direction.UNDIRECTED) List<Peer> peers) {
}

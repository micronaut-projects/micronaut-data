package io.micronaut.data.oracle.graph.r2dbc.wallet;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

/**
 * A node with a single relationship, which has no column in the vertex table.
 */
@Node(value = "vaults", labels = "Vault")
public record Vault(@Id Long id, String name, @Nullable @Relationship("BACKED_BY") Vault backup) {
}

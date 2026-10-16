package io.micronaut.data.oracle.graph.r2dbc.wallet;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node(value = "wallets", labels = "Wallet")
public record Wallet(@Id Long id, String owner, @Relationship("PAID") List<Payment> payments) {
}

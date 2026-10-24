package io.micronaut.data.oracle.graph.r2dbc.wallet;

import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

import java.math.BigDecimal;

@RelationshipProperties
public record Payment(@RelationshipId Long id, BigDecimal amount, @TargetNode Wallet to) {
}

package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

import java.math.BigDecimal;

@RelationshipProperties
public record Transfer(@RelationshipId Long id, BigDecimal amount, @TargetNode Account to) {
}

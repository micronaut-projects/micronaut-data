package io.micronaut.data.oracle.graph.bank;

import io.micronaut.core.annotation.Introspected;

import java.math.BigDecimal;

@Introspected
public record TransferView(String source, String target, BigDecimal amount) {
}

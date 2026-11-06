package io.micronaut.data.oracle.graph.bank;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;

@Serdeable
public record TransferView(String source, String target, BigDecimal amount) {
}

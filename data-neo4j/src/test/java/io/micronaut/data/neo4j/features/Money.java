package io.micronaut.data.neo4j.features;

import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) {
}

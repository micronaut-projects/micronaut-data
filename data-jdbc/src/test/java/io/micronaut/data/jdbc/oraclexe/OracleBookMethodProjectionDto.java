package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record OracleBookMethodProjectionDto(
    String bookTitle,
    int pageCount
) {
}

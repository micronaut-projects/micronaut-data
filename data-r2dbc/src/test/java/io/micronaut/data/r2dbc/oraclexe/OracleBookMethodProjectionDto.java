package io.micronaut.data.r2dbc.oraclexe;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record OracleBookMethodProjectionDto(
    String bookTitle,
    int pageCount
) {
}

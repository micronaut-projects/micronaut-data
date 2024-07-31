package io.micronaut.data.r2dbc.h2.identity;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MyBookDto2(
        Integer id_renamed,
        String title
) {
}

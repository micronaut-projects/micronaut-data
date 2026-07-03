package io.micronaut.data.jdbc.sqlite.identity;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MyBookDto(
        Integer id,
        String title
) {
}

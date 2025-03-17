package io.micronaut.data.jdbc.h2.identity;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MyBookDto(
        Integer id,
        String title
) {
}

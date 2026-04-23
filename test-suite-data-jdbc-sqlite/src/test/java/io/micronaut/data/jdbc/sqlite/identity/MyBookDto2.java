package io.micronaut.data.jdbc.sqlite.identity;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MyBookDto2(
        Integer id_renamed,
        String title
) {
}

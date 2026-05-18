package io.micronaut.data.jdbc.h2.autopopulate;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Embeddable;

import java.util.UUID;

@Introspected
@Embeddable
public record ImmutableEmbedWithUUID(
    @AutoPopulated(skipIfPresent = true) UUID embId
) {
}

package io.micronaut.data.model.schema;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

@Internal
public record Column(
    String name,
    int sqlType,
    boolean primaryKey,
    boolean nullable,
    boolean generated,
    @Nullable Integer length,
    @Nullable Integer scale,
    @Nullable Integer precision
) {
}

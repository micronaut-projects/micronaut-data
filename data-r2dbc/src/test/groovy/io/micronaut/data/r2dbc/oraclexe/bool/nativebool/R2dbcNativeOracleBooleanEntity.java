package io.micronaut.data.r2dbc.oraclexe.bool.nativebool;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

@MappedEntity
public record R2dbcNativeOracleBooleanEntity(
    @Id @GeneratedValue
    Long id,
    @Nullable
    Boolean active
) {
}

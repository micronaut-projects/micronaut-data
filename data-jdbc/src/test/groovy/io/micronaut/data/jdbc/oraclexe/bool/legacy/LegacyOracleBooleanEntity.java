package io.micronaut.data.jdbc.oraclexe.bool.legacy;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

@MappedEntity
public record LegacyOracleBooleanEntity(
    @Id @GeneratedValue
    Long id,
    @Nullable
    Boolean active
) {
}

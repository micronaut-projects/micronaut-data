package io.micronaut.data.jdbc.oraclexe.bool.detect;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

@MappedEntity
public record DetectedVersionBooleanEntity(
    @Id @GeneratedValue
    @Nullable
    Long id,
    @Nullable
    Boolean active
) {
}

package io.micronaut.data.jdbc.oraclexe.bool;

import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.Column;

@MappedEntity
public record BooleanTest(
    @Id @GeneratedValue
    Long id,
    @Column(nullable = true)
    @Nullable
    Boolean canBeNull
) {
}

package io.micronaut.data.jdbc.oraclexe.ddl;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@MappedEntity
@Index(columns = "name")
public record OracleUuidDdlEntity(
    @Id @GeneratedValue
    @Nullable
    UUID id,
    String name
) {
}

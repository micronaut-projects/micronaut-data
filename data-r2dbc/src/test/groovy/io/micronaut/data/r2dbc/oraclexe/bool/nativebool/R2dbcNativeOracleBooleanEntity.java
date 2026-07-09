package io.micronaut.data.r2dbc.oraclexe.bool.nativebool;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import org.jspecify.annotations.Nullable;

@MappedEntity
public record R2dbcNativeOracleBooleanEntity(
    @Id @GeneratedValue
    Long id,
    @TypeDef(type = DataType.BOOLEAN)
    @Nullable
    Boolean active
) {
}

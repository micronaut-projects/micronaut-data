package io.micronaut.data.r2dbc.oraclexe.jsonviewoptions;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_SUPPLIER", alias = "su")
public record Supplier(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @Nullable
    Long id,
    String name,
    String contactEmail
) {
}

package io.micronaut.data.r2dbc.oraclexe.jsonviewoptions;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity(value = "TBL_PRODUCT", alias = "pr")
public record Product(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @Nullable
    Long id,
    String name,
    String region,
    @Nullable
    Double price,
    @Nullable
    Integer quantity,
    String sku,
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    Supplier supplier
) {
}

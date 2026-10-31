package io.micronaut.data.r2dbc.oraclexe.jsonviewoptions;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.JsonViewField;
import io.micronaut.data.annotation.JsonViewGenerated;
import io.micronaut.data.annotation.Relation;

@JsonView(entity = Product.class, where = "@.region = 'EU'", hidden = {"price", "quantity"})
public record ProductView(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @Nullable
    Long id,
    String name,
    String region,
    @JsonViewField(updatable = false)
    String sku,
    @Nullable
    @JsonViewGenerated(path = "$.price * $.quantity")
    Double total,
    @Nullable
    @JsonViewGenerated(sql = "UPPER(@.name)")
    String label,
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    SupplierSubView supplier
) {
}

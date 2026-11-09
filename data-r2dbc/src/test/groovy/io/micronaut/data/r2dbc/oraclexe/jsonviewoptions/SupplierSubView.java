package io.micronaut.data.r2dbc.oraclexe.jsonviewoptions;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.JsonViewGenerated;
import io.micronaut.data.annotation.MappedProperty;

@Embeddable
@JsonSubView(entity = Supplier.class, operations = JsonView.Operation.UPDATE, hidden = "contactEmail", etagCheck = false)
public record SupplierSubView(
    @Id
    @MappedProperty("id")
    Long supplierId,
    String name,
    @Nullable
    @JsonViewGenerated(sql = "SUBSTR(@.contact_email, INSTR(@.contact_email, '@') + 1)")
    String domain
) {
}

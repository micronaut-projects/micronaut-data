package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_SHIPMENT", alias = "sh")
public record Shipment(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @Nullable
    Long id,
    String reference,
    String country,
    @Nullable
    Double weight,
    @Nullable
    Double rate,
    String trackingCode
) {}

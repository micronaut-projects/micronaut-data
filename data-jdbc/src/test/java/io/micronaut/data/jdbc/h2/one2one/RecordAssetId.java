package io.micronaut.data.jdbc.h2.one2one;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;

import java.util.UUID;

@Embeddable
public record RecordAssetId(
    @MappedProperty("container_id") UUID containerId,
    @MappedProperty("asset_id") Integer assetId
) {
}

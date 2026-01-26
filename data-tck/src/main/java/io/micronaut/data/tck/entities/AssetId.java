package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;

import java.util.UUID;

@Embeddable
public record AssetId(
        @MappedProperty("container_id") UUID containerId,
        @MappedProperty("asset_id") int assetId
) { }

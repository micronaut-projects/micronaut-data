package io.micronaut.data.jdbc.h2.one2one;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import jakarta.persistence.JoinColumn;

@MappedEntity("record_asset")
public record RecordAsset(
    @EmbeddedId RecordAssetId id,
    String title,
    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
    @JoinColumn(name = "container_id", referencedColumnName = "container_id")
    @JoinColumn(name = "asset_id", referencedColumnName = "asset_id")
    RecordAssetMetadata metadata
) {
}

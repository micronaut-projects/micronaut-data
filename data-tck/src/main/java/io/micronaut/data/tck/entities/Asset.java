package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;

@MappedEntity("asset")
public record Asset(
        @EmbeddedId AssetId id,
        String title,
        @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
        @JoinColumn(name = "container_id", referencedColumnName = "container_id")
        @JoinColumn(name = "asset_id", referencedColumnName = "asset_id")
        @Nullable AssetMetadata metadata
) { }

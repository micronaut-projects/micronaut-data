package io.micronaut.data.jdbc.h2.one2one;

import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("record_assetmetadata")
public record RecordAssetMetadata(
    @EmbeddedId RecordAssetId id,
    String author
) {
}

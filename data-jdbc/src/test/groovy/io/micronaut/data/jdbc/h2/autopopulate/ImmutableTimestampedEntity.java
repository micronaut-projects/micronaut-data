package io.micronaut.data.jdbc.h2.autopopulate;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.UUID;

import static io.micronaut.data.annotation.Relation.Kind.EMBEDDED;

@Introspected
@MappedEntity("immutable_ts_skip_ap")
public record ImmutableTimestampedEntity(
    @Id @AutoPopulated UUID id,
    String name,
    @Relation(EMBEDDED) ImmutableAuditTimestamps audit
) {
}

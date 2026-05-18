package io.micronaut.data.jdbc.h2.autopopulate;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Embeddable;

import java.time.Instant;

@Introspected
@Embeddable
public record ImmutableAuditTimestamps(
    @DateCreated(skipIfPresent = true) Instant dateCreated,
    @DateUpdated(skipIfPresent = true) Instant dateUpdated
) {
}

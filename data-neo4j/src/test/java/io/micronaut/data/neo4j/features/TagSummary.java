package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Projection;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A DTO whose property selects a column under another name.
 */
@Serdeable
public record TagSummary(@Projection("name") String label) {
}

package io.micronaut.data.model.schema;

import io.micronaut.core.annotation.Internal;

/**
 * The table sequence.
 *
 * @param definition The custom definition as SQL command to be executed to create sequence
 * @param name The sequence name to be created if definition not provided
 */
@Internal
public record Sequence(String definition, String name) {
}

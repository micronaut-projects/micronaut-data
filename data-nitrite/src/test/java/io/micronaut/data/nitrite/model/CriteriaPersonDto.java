package io.micronaut.data.nitrite.model;

import io.micronaut.core.annotation.Introspected;

/**
 * Projection used by the Criteria repository tests.
 */
@Introspected
public record CriteriaPersonDto(String name, int age) {
}

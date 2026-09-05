package io.micronaut.data.nitrite.generated;

import io.micronaut.core.annotation.Introspected;

/**
 * Projection used by the generated-query fallback suite.
 *
 * @param title book title
 * @param pages page count
 */
@Introspected
public record GeneratedQueryBookDto(String title, int pages) {
}

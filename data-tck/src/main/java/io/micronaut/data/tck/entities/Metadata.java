package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

/**
 * The Json Duality View metadata.
 *
 * @param etag A unique identifier for a specific version of the document, as a string of hexadecimal characters.
 * @param asof The latest system change number (SCN) for the JSON document, as a JSON number.
 *             This records the last logical point in time at which the document was generated.
 */
@Introspected
public record Metadata(

    String etag,

    String asof
) {
}

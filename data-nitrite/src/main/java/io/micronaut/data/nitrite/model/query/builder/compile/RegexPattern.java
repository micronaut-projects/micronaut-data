package io.micronaut.data.nitrite.model.query.builder.compile;

/**
 * Tagged wrapper around a regex string used during JSON query serialization.
 *
 * @param value the regex value
 */
public record RegexPattern(String value) { }

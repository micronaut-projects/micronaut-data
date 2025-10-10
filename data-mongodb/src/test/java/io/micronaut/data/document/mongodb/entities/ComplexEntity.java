package io.micronaut.data.document.mongodb.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * An entity with one of field being complex field ie. object.
 * @param id The id
 * @param simpleValue The simple value field (String)
 * @param complexValue The complex value field (Object)
 */
@MappedEntity
public record ComplexEntity (
    @Id
    @GeneratedValue
    String id,
    String simpleValue,
    ComplexValue complexValue) {
    ComplexEntity(String simpleValue, ComplexValue complexValue) {
        this(null, simpleValue, complexValue);
    }
}

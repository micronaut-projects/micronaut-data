package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Where;

@MappedEntity
@Where("@.deleted = false")
public record DocumentType(
        @Id
        @GeneratedValue
        Long id,
        String name,
        Boolean deleted
) {
    public DocumentType {
        deleted = false;
    }
}

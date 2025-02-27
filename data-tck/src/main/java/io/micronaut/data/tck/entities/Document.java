package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.UUID;

@MappedEntity
public record Document(
        @Id
        @GeneratedValue
        UUID id,
        String name,
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        @Nullable
        DocumentType type) {
}

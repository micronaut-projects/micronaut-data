package io.micronaut.data.tck.entities.embedded;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.Embedded;

@MappedEntity
public record BookEntity(
    @Id Long id,
    @Embedded ResourceEntity<BookState> resource
) implements BaseEntity<Long, BookState> {
}

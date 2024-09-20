package io.micronaut.data.jdbc.h2.joinissue;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

@MappedEntity("ji_book")
public record Book(
        @Id
        @GeneratedValue
        Long id,
        String title,
        @Nullable
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        @MappedProperty("author")
        Author author) {
    public Book(Long id, String title) {
        this(id, title, null);
    }
}


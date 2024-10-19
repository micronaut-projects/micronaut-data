package io.micronaut.data.jdbc.h2.joinissue;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.Set;

@MappedEntity("ji_author")
public record Author(
        @Id
        @GeneratedValue
        Long id,
        String name,
        @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.ALL, mappedBy = "author")
        Set<Book> books) {
}

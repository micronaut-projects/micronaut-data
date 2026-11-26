package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

/**
 * Not escaped, so its table, its join table and their columns are not quoted, while {@link Book} is quoted.
 */
@MappedEntity(value = "TBL_SHELF", alias = "sh", escape = false)
public record Shelf(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    @Relation(Relation.Kind.ONE_TO_MANY)
    List<Book> books
) {}

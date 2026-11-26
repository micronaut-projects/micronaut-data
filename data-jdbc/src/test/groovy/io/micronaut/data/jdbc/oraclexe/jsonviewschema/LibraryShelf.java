package io.micronaut.data.jdbc.oraclexe.jsonviewschema;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinTable;

import java.util.List;

/**
 * Its join table has no schema of its own, so it is created in this entity's schema. The name of the join table and
 * the column {@code COMMENT}, an Oracle reserved word, only work quoted.
 */
@MappedEntity(value = "TBL_LIB_SHELF", schema = "LIBRARY", alias = "lsh")
public record LibraryShelf(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    String comment,
    @Relation(Relation.Kind.ONE_TO_MANY)
    @JoinTable(name = "SHELF-LINKS")
    List<LibraryBook> books
) {}

package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

/**
 * Not escaped, so its table and its foreign key column are not quoted, while {@link Book} is quoted.
 */
@MappedEntity(value = "TBL_LOAN", alias = "ln", escape = false)
public record Loan(
    @Id
    Long id,
    @Relation(Relation.Kind.MANY_TO_ONE)
    Book book
) {}

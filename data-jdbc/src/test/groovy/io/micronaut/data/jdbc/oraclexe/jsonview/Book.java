package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

/**
 * Its id column only works quoted.
 */
@MappedEntity(value = "TBL_BOOK", alias = "bk")
public record Book(
    @Id
    @MappedProperty("BOOK-KEY")
    Long id,
    String title
) {}

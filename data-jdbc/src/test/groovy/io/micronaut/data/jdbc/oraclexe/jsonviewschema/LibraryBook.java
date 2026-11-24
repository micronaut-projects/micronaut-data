package io.micronaut.data.jdbc.oraclexe.jsonviewschema;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_LIB_BOOK", schema = "LIBRARY", alias = "lbk")
public record LibraryBook(
    @Id
    Long id,
    String title
) {}

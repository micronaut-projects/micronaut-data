package io.micronaut.data.jdbc.oraclexe.jsonviewschema;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubView(entity = LibraryBook.class, alias = "lbksw")
public record LibraryBookSubView(
    @Id
    Long id,
    String title
) {}

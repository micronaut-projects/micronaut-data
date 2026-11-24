package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubView(entity = Book.class, alias = "bksw")
public record BookSubView(
    @Id
    Long id,
    String title
) {}

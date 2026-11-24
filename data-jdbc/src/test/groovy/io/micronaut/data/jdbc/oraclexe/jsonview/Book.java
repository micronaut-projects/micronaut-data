package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "TBL_BOOK", alias = "bk")
public record Book(
    @Id
    Long id,
    String title
) {}

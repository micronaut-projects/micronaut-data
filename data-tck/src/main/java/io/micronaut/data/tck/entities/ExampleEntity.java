package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public record ExampleEntity(
    @Id Integer id,
    @Nullable @Column(name = "UPPERCASE_COLUMN") String uppercaseColumn,
    @Nullable @Column(name = "lowercase_column") String lowercaseColumn) {
}

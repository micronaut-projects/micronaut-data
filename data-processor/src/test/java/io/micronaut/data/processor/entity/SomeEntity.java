package io.micronaut.data.processor.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity(name = "some_table")
@Table(name = "some_table")
public record SomeEntity(@EmbeddedId
                  PrimaryKey primaryKey,
                  String col) {

    @Embeddable
    public record PrimaryKey(
            int someColumn,
            @ManyToOne OtherEntity otherEntity
    ) {
    }
}

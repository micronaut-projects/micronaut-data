package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

@MappedEntity
public record Crocodile(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    String name,
    @Relation(Relation.Kind.EMBEDDED)
    Characteristics characteristics
) {
    @Embeddable
    @Serdeable
    public record Characteristics(
        double weight,
        double length
    ) {
    }
}

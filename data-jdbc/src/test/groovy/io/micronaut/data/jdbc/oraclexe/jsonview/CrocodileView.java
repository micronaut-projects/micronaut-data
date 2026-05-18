package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.oraclexe.jsonview.Crocodile.Characteristics;
import io.micronaut.serde.annotation.Serdeable;

@JsonView(entity = Crocodile.class)
@Serdeable
public record CrocodileView(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    String name,
    @Relation(Relation.Kind.EMBEDDED)
    Characteristics characteristics
) {
}

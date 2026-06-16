package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity(value = "TBL_AIRPLANE")
public record Airplane (
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    @Relation(Relation.Kind.EMBEDDED)
    AirplaneDetails airplaneDetails
) {}

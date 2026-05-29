package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinTable;

import java.util.List;

@MappedEntity(value = "TBL_CAR", alias = "car")
public record Car (
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    @Relation(Relation.Kind.ONE_TO_ONE)
    CarDetails carDetails
) {}

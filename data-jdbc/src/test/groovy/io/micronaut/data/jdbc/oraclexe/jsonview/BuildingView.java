package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@JsonView(entity = Building.class, alias = "bw")
public record BuildingView (
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    @Relation(Relation.Kind.ONE_TO_MANY)
    List<ApartmentSubView> apartments
) {}

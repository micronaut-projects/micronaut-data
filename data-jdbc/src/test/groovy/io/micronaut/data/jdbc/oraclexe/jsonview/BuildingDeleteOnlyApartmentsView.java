package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;

import java.util.List;

/**
 * Building view whose apartment sub view declares only DELETE, which the apartment table can't take.
 */
@JsonView(entity = Building.class, alias = "bdw")
public record BuildingDeleteOnlyApartmentsView(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    @Relation(Relation.Kind.ONE_TO_MANY)
    List<DeleteOnlyApartmentSubView> apartments
) {}

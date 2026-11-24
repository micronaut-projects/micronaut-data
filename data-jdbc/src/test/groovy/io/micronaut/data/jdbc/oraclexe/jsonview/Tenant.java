package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity(value = "TBL_TENANT", alias = "tn")
public record Tenant(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    String name,
    @Relation(Relation.Kind.MANY_TO_MANY)
    List<Apartment> apartments
) {}

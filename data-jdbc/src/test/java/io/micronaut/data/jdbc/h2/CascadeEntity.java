package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity
public record CascadeEntity(
        @Id @GeneratedValue Long id,
        @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.ALL, mappedBy = "entity")
        List<CascadeSubEntityA> subEntityAs,
        @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.ALL, mappedBy = "entity")
        List<CascadeSubEntityB> subEntityBs
){};

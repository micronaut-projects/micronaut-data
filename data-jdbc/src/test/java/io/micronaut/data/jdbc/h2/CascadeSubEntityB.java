package io.micronaut.data.jdbc.h2;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity
public record CascadeSubEntityB(
        @Id @GeneratedValue Long id,
        @Nullable Integer data,
        @Relation(Relation.Kind.MANY_TO_ONE)
        @Nullable
        CascadeEntity entity
){};

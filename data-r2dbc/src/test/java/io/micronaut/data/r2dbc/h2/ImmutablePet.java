package io.micronaut.data.r2dbc.h2;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@Introspected
@MappedEntity
public class ImmutablePet {
    @GeneratedValue
    @Id
    @Nullable
    private final Long id;
    @Nullable
    private final String name;

    public ImmutablePet(@Nullable Long id, @Nullable String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

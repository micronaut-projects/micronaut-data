package io.micronaut.data.tck.entities.embedded;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import jakarta.persistence.Embedded;

@MappedEntity
public record HouseEntity(
    @Id Long id,
    @Embedded ResourceEntity<HouseState> resource
) implements BaseEntity<Long, HouseState> {
}

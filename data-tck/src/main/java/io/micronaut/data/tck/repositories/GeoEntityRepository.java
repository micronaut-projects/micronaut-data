package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.GeoEntity;

public interface GeoEntityRepository extends CrudRepository<GeoEntity, Long> {
}

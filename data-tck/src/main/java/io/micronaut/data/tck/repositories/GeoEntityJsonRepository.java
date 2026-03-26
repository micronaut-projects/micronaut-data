package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.geo.GeoEntityJson;

public interface GeoEntityJsonRepository extends CrudRepository<GeoEntityJson, Long> {
}

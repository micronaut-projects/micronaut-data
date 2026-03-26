package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.geo.GeoEntityWkt;

public interface GeoEntityWktRepository extends CrudRepository<GeoEntityWkt, Long> {
}

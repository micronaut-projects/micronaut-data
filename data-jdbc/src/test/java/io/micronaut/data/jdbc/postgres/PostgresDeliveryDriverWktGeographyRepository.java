package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresDeliveryDriverWktGeographyRepository extends CrudRepository<DeliveryDriverWktGeography, Long> {

    List<DeliveryDriverWktGeography> findByStatusAndLocationNear(DeliveryDriverWktGeography.Status status, Point orderLocation, double maxDistanceMeters);
}

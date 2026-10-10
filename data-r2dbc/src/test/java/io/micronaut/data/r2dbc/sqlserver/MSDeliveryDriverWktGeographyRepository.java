package io.micronaut.data.r2dbc.sqlserver;

import io.micronaut.data.model.geo.LineString;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.geo.Polygon;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@R2dbcRepository(dialect = Dialect.SQL_SERVER)
public interface MSDeliveryDriverWktGeographyRepository extends CrudRepository<DeliveryDriverWktGeography, Long> {

    List<DeliveryDriverWktGeography> findByStatusAndLocationNear(DeliveryDriverWktGeography.Status status, Point orderLocation, double maxDistanceMeters);

    List<DeliveryDriverWktGeography> findByLocationGeoWithin(Polygon region);

    List<DeliveryDriverWktGeography> findByLocationGeoIntersects(LineString route);
}

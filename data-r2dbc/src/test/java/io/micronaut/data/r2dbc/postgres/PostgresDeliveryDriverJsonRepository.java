package io.micronaut.data.r2dbc.postgres;

import io.micronaut.data.model.geo.Polygon;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.jdbc.entities.geo.DeliveryDriverJson;
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository;

import java.util.List;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresDeliveryDriverJsonRepository extends DeliveryDriverJsonRepository {

    List<DeliveryDriverJson> findByLocationGeoWithin(Polygon region);
}

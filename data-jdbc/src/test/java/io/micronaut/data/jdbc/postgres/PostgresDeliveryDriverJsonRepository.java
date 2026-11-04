package io.micronaut.data.jdbc.postgres;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.geo.Polygon;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.jdbc.entities.geo.DeliveryDriverJson;
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresDeliveryDriverJsonRepository extends DeliveryDriverJsonRepository {

    List<DeliveryDriverJson> findByLocationGeoWithin(Polygon region);
}

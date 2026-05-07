package io.micronaut.data.tck.jdbc.entities.geo;

import example.Hotel;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.geo.Polygon;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.MYSQL)
public interface HotelRepository extends CrudRepository<Hotel, Long> {

    List<Hotel> findByLocationGeoWithin(Polygon city);
}

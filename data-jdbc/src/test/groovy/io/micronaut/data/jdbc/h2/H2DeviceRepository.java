package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.tck.repositories.DeviceRepository;

import static io.micronaut.data.model.query.builder.sql.Dialect.H2;

@JdbcRepository(dialect = H2)
public interface H2DeviceRepository extends DeviceRepository {
}

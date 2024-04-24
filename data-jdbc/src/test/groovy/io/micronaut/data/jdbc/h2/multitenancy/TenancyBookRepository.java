package io.micronaut.data.jdbc.h2.multitenancy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@Requires(property = "spec.name", value = "TenancyBookControllerSpec")
@JdbcRepository(dialect = Dialect.H2) // <1>
public interface TenancyBookRepository extends CrudRepository<TenancyBook, Long> {  // <2>
    Long save(String title);

    @WithoutTenantId
    @Override
    void deleteAll();
}

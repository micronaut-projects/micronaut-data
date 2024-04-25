package io.micronaut.data.jdbc.h2.multitenancy;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@Requires(property = "spec.name", value = "TenancyBookControllerSpec")
//tag::clazz[]
@JdbcRepository(dialect = Dialect.H2)
public interface TenancyBookRepository extends CrudRepository<TenancyBook, Long> {
    Long save(String title);

    @WithoutTenantId
    long count();

    @WithoutTenantId
    void removeAll();
}
//end::clazz[]

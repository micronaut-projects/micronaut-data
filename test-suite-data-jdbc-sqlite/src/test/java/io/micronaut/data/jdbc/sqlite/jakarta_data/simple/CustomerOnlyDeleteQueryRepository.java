package io.micronaut.data.jdbc.sqlite.jakarta_data.simple;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

import java.util.UUID;

@Repository
@JdbcRepository(dialect = Dialect.SQLITE)
public interface CustomerOnlyDeleteQueryRepository {
    @Query("""
    DELETE FROM CUSTOMERS WHERE ID=:id
    """)
    @Transactional
    void deleteById(@Param("id") UUID id);
}

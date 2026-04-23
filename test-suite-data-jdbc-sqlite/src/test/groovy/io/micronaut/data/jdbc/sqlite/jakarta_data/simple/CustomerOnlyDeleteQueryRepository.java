package io.micronaut.data.jdbc.sqlite.jakarta_data.simple;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static jakarta.data.repository.By.ID;

@Repository
@JdbcRepository(dialect = Dialect.ANSI)
public interface CustomerOnlyDeleteQueryRepository {
    @Query("""
    DELETE FROM CUSTOMERS WHERE ID=:id
    """)
    @Transactional
    void deleteById(@Param("id") UUID id);
}

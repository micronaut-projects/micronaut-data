package io.micronaut.data.jdbc.sqlite.jakarta_data.simple;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@JdbcRepository(dialect = Dialect.ANSI)
public interface CustomerRepository2 {

    @Insert
    void insert(Customer customer);

    @Query("""
    WHERE ID=:id
    """)
    // The entity is detected by the Insert operation
    Customer find(@Param("id") UUID id);

    @Query("""
    WHERE ID=:id
    """)
    // The entity is detected by the Insert operation
    List<Customer> find2(@Param("id") UUID id);
}

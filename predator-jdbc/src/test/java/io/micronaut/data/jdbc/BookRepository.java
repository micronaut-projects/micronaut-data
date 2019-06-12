package io.micronaut.data.jdbc;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.jdbc.annotation.*;

@JdbcRepository
public interface BookRepository extends CrudRepository<Book, Long> {
}

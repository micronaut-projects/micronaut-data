package io.micronaut.data.jdbc.h2.snake;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
public interface CBookRepository extends CrudRepository<CBook, Long> {

    Optional<CBook> find_by_total_pages(int pages);

    Optional<CBook> findByTotalPages(int pages);

    List<CBook> find_by_author_name(String name);

    List<CBook> findByAuthorName(String name);
}

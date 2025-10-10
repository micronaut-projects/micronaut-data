package io.micronaut.data.jdbc.h2.identity;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public interface MyBookRepository extends CrudRepository<MyBook, Integer> {
    @Query("""
        SELECT 1 AS id, 'Title #1' AS title
        UNION ALL
        SELECT 1 AS id, 'Title #2' AS title
        """)
    List<MyBook> getBooks();

    @Query("""
        SELECT 1 AS id, 'Title #1' AS title
        UNION ALL
        SELECT 1 AS id, 'Title #2' AS title
        """)
    List<MyBookDto> getBooksAsDto();

    @Query("""
        SELECT 1 AS id_renamed, 'Title #1' AS title
        UNION ALL
        SELECT 1 AS id_renamed, 'Title #2' AS title
        """)
    List<MyBookDto2> getBooksAsDto2();
}

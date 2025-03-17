package io.micronaut.data.r2dbc.h2.identity;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;
import reactor.core.publisher.Flux;

@R2dbcRepository(dialect = Dialect.H2)
public interface MyBookRepository extends CrudRepository<MyBook, Integer> {
    @Query("""
        SELECT 1 AS id, 'Title #1' AS title
        UNION ALL
        SELECT 1 AS id, 'Title #2' AS title
        """)
    Flux<MyBook> getBooks();

    @Query("""
        SELECT 1 AS id, 'Title #1' AS title
        UNION ALL
        SELECT 1 AS id, 'Title #2' AS title
        """)
    Flux<MyBookDto> getBooksAsDto();

    @Query("""
        SELECT 1 AS id_renamed, 'Title #1' AS title
        UNION ALL
        SELECT 1 AS id_renamed, 'Title #2' AS title
        """)
    Flux<MyBookDto2> getBooksAsDto2();
}

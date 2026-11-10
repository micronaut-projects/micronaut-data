package io.micronaut.data.oracle.graph.r2dbc.library;

import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@OracleGraphR2dbcRepository(graph = "library_graph")
public interface BookRepository extends ReactorCrudRepository<Book, Long> {

    Flux<Book> findByAuthorsName(String name);
}

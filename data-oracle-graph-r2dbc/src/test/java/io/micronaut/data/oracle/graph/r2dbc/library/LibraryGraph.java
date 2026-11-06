package io.micronaut.data.oracle.graph.r2dbc.library;

import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@OracleGraphR2dbcRepository(graph = "library_graph")
public interface LibraryGraph extends ReactorCrudRepository<Member, Long> {

    Flux<Member> findByLoansBookTitle(String title);

    Flux<Member> findByLoansDaysGreaterThan(int days);

    Flux<Member> findByFollowsName(String name);
}

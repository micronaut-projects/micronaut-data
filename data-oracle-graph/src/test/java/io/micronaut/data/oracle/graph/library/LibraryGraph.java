package io.micronaut.data.oracle.graph.library;

import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@OracleGraphRepository(graph = "library_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ)
public interface LibraryGraph extends CrudRepository<Member, Long> {

    List<Member> findByLoansBookTitle(String title);

    List<Member> findByLoansDaysGreaterThan(int days);

    List<Member> findByFollowsName(String name);
}

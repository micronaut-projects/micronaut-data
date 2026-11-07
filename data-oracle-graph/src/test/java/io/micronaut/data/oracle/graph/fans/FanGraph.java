package io.micronaut.data.oracle.graph.fans;

import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@OracleGraphRepository(graph = "fan_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ)
public interface FanGraph extends CrudRepository<Fan, Long> {

    List<Fan> findByFirstName(String firstName);

    List<Fan> findByFilmsTitle(String title);

    List<Fan> findByNovelsTitle(String title);
}

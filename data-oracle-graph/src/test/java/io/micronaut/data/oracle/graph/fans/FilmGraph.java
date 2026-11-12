package io.micronaut.data.oracle.graph.fans;

import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@OracleGraphRepository(graph = "fan_graph")
public interface FilmGraph extends CrudRepository<Film, Long> {

    List<Film> findByFansFirstName(String firstName);
}

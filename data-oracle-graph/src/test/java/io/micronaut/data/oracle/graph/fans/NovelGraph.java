package io.micronaut.data.oracle.graph.fans;

import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@OracleGraphRepository(graph = "fan_graph")
public interface NovelGraph extends CrudRepository<Novel, Long> {

    List<Novel> findBySequelsTitle(String title);

    List<Novel> findByPrequelsTitle(String title);
}

package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.graph.tck.entities.Colleague;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

public interface ColleagueRepository extends CrudRepository<Colleague, String> {

    List<Colleague> findByColleaguesProject(String project);

    @Query("MATCH (a:Colleague)-[r:WORKS_WITH]->(b:Colleague) RETURN count(r)")
    long countCollaborations();
}

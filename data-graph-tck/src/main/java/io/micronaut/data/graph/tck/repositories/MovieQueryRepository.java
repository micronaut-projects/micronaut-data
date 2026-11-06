package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.graph.tck.entities.Person;

import java.util.List;
import java.util.Optional;

/**
 * Fetch joins and Cypher queries, for the databases that run Cypher.
 */
public interface MovieQueryRepository extends MovieRepository {

    @Join("roles")
    @Join("directors")
    Optional<Movie> findByTitle(String title);

    @Query("MATCH (m:Movie) WHERE m.title CONTAINS $title RETURN m")
    List<Movie> search(String title);

    @Query("MATCH (m:Movie {title: $title}) SET m.votes = coalesce(m.votes, 0) + 1")
    void vote(String title);

    @Query("MATCH (p:Person)-[r:ACTED_IN]->(m:Movie) WHERE m.title = $title RETURN r, p AS f, m AS t")
    List<GraphRelationship<Person, Movie>> findActedIn(String title);
}

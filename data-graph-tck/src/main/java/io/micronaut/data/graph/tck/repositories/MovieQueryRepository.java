package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.graph.tck.entities.MovieDirectors;
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

    @Join("directors")
    @Join("directors.mentor")
    Optional<Movie> queryByTitle(String title);

    @Join(value = "directors", type = Join.Type.INNER_FETCH)
    @Join(value = "directors.mentor", type = Join.Type.INNER_FETCH)
    List<Movie> queryByReleasedGreaterThan(int released);

    // The inner join comes last: a later @Join through directors would replace the join type of directors
    @Join("directors.mentor")
    @Join(value = "directors", type = Join.Type.INNER_FETCH)
    List<Movie> listByReleasedGreaterThan(int released);

    @Query("MATCH (p:Person), (m:Movie) WHERE p.name = $name AND m.title = $title CREATE (p)-[:DIRECTED]->(m)")
    void addDirector(String name, String title);

    @Query("MATCH (p:Person)-[:DIRECTED]->(m:Movie) WHERE m.title = $title RETURN m.title AS title, collect(p.name) AS directors")
    Optional<MovieDirectors> findDirectorNames(String title);

    @Query("MATCH (m:Movie {title: $title}) OPTIONAL MATCH (m)<-[:DIRECTED]-(p:Person) RETURN m, collect(p) AS j_directors")
    Optional<Movie> findWithDirectors(String title);
}

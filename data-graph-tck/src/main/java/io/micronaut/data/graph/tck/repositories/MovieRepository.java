package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.graph.tck.entities.MovieSummary;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

/**
 * Repository methods every graph database supports. {@link MovieQueryRepository} adds fetch joins and Cypher queries.
 */
public interface MovieRepository extends CrudRepository<Movie, String> {

    List<Movie> findByReleasedGreaterThanOrderByReleased(int year);

    List<Movie> findByTitleContainsIgnoreCase(String part);

    long countByReleasedLessThan(int year);

    boolean existsByTitle(String title);

    List<Movie> findByDirectorsName(String name);

    List<Movie> findByRolesPersonName(String name);

    List<String> findTitleByReleasedGreaterThanOrderByTitle(int released);

    Integer findMaxReleased();

    List<MovieSummary> findByReleasedBetweenOrderByReleased(int from, int to);

    void updateReleased(@Id String title, Integer released);

    long deleteByReleasedLessThan(Integer released);

    Page<Movie> findByReleasedGreaterThan(int released, Pageable pageable);

    boolean existsRelationshipByDirectorsBetweenTitleAndName(String title, String name);

    long countRelationshipsByDirectors();
}

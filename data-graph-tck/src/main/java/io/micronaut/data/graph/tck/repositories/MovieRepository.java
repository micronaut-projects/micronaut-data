package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.graph.tck.entities.MovieSummary;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends CrudRepository<Movie, String> {

    List<Movie> findByReleasedGreaterThanOrderByReleased(int year);

    List<Movie> findByTitleContainsIgnoreCase(String part);

    long countByReleasedLessThan(int year);

    boolean existsByTitle(String title);

    List<Movie> findByDirectorsName(String name);

    List<Movie> findByRolesPersonName(String name);

    @Join("roles")
    @Join("directors")
    Optional<Movie> findByTitle(String title);

    List<String> findTitleByReleasedGreaterThanOrderByTitle(int released);

    Integer findMaxReleased();

    List<MovieSummary> findByReleasedBetweenOrderByReleased(int from, int to);

    void updateReleased(@Id String title, Integer released);

    long deleteByReleasedLessThan(Integer released);

    Page<Movie> findByReleasedGreaterThan(int released, Pageable pageable);

    @Query("MATCH (m:Movie) WHERE m.title CONTAINS $title RETURN m")
    List<Movie> search(String title);

    @Query("MATCH (m:Movie {title: $title}) SET m.votes = coalesce(m.votes, 0) + 1")
    void vote(String title);
}

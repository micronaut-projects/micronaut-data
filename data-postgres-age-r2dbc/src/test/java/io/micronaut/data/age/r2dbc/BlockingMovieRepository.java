package io.micronaut.data.age.r2dbc;

import io.micronaut.data.age.r2dbc.annotation.AgeR2dbcRepository;
import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.repository.CrudRepository;

@AgeR2dbcRepository
public interface BlockingMovieRepository extends CrudRepository<Movie, String> {
}

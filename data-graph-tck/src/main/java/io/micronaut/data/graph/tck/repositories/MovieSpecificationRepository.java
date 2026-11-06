package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

public interface MovieSpecificationRepository extends GenericRepository<Movie, String>, JpaSpecificationExecutor<Movie> {
}

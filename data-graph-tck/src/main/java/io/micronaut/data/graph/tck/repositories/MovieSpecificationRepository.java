package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.model.Limit;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

import java.util.List;

public interface MovieSpecificationRepository extends GenericRepository<Movie, String>, JpaSpecificationExecutor<Movie> {

    List<Movie> findAll(PredicateSpecification<Movie> spec, Limit limit);
}

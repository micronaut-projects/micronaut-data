package io.micronaut.data.graph.processor;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A graph repository stereotype for compile time tests.
 */
@RepositoryConfiguration(
    queryBuilder = CypherQueryBuilder.class,
    implicitQueries = true,
    namedParameters = true
)
@Retention(RetentionPolicy.RUNTIME)
@Repository
@GraphRepository
public @interface TestGraphRepository {
}

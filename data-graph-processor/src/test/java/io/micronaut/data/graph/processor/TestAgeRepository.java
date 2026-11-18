package io.micronaut.data.graph.processor;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.model.query.builder.AgeCypherQueryBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An Apache AGE repository stereotype for compile time tests.
 */
@RepositoryConfiguration(
    queryBuilder = AgeCypherQueryBuilder.class,
    implicitQueries = true,
    namedParameters = true
)
@Retention(RetentionPolicy.RUNTIME)
@Repository
@GraphRepository
public @interface TestAgeRepository {
}

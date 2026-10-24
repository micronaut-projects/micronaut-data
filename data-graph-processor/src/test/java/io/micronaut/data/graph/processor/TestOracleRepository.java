package io.micronaut.data.graph.processor;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An Oracle property graph repository stereotype for compile time tests.
 */
@RepositoryConfiguration(
    queryBuilder = OracleGraphQueryBuilder.class,
    implicitQueries = false,
    namedParameters = false
)
@Retention(RetentionPolicy.RUNTIME)
@Repository
@GraphRepository
public @interface TestOracleRepository {
}

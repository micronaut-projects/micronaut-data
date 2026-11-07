package io.micronaut.data.graph.processor;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A stereotype like {@code @OracleGraphR2dbcRepository}, which this module cannot depend on.
 */
@RepositoryConfiguration(
    queryBuilder = OracleGraphQueryBuilder.class,
    implicitQueries = false,
    namedParameters = false
)
@Retention(RetentionPolicy.RUNTIME)
@Repository
@GraphRepository(nonBlocking = true)
@PropertyGraph(language = PropertyGraph.QueryLanguage.SQL_PGQ)
public @interface TestNonBlockingRepository {
    String graph();
}

/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.oracle.graph.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;
import io.micronaut.data.oracle.graph.operations.OracleGraphRepositoryOperations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stereotype that configures a {@link Repository} for an Oracle property graph defined over tables with
 * {@code CREATE PROPERTY GRAPH}. Queries are compiled at build time to PGQL, which needs a data source that uses
 * the PGQL JDBC driver, or to SQL/PGQ {@code GRAPH_TABLE} queries, which run on any Oracle 23ai data source.
 *
 * <p>Property graphs over tables are read only. Write the tables with a JDBC repository.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@RepositoryConfiguration(
    queryBuilder = OracleGraphQueryBuilder.class,
    operations = OracleGraphRepositoryOperations.class,
    implicitQueries = false,
    namedParameters = false
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
@GraphRepository
public @interface OracleGraphRepository {

    /**
     * @return The name of the data source
     */
    @AliasFor(annotation = Repository.class, member = "value")
    String value() default "default";

    /**
     * @return The name of the property graph
     */
    @AliasFor(annotation = PropertyGraph.class, member = "value")
    String graph();

    /**
     * @return The query language, PGQL by default
     */
    @AliasFor(annotation = PropertyGraph.class, member = "language")
    PropertyGraph.QueryLanguage language() default PropertyGraph.QueryLanguage.PGQL;
}

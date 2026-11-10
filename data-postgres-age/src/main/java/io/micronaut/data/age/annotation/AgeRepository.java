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
package io.micronaut.data.age.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.age.operations.AgeRepositoryOperations;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.graph.annotation.GraphRepository;
import io.micronaut.data.graph.model.query.builder.AgeCypherQueryBuilder;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stereotype that configures a {@link Repository} for a graph in PostgreSQL with Apache AGE.
 * Queries are compiled to Cypher at build time and run through the {@code cypher()} function.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@RepositoryConfiguration(
    queryBuilder = AgeCypherQueryBuilder.class,
    operations = AgeRepositoryOperations.class,
    implicitQueries = true,
    namedParameters = true
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
@GraphRepository
public @interface AgeRepository {

    /**
     * @return The name of the data source
     */
    @AliasFor(annotation = Repository.class, member = "value")
    String value() default "default";
}

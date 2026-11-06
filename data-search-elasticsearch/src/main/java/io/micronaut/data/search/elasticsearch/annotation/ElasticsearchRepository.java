/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.search.elasticsearch.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.search.document.query.DocumentSearchQueryBuilder;
import io.micronaut.data.search.document.runtime.DocumentSearchRepositoryOperations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository backed by an Elasticsearch index.
 *
 * @since 5.4.0
 */
@RepositoryConfiguration(
    queryBuilder = DocumentSearchQueryBuilder.class,
    operations = DocumentSearchRepositoryOperations.class,
    implicitQueries = false,
    namedParameters = false
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
public @interface ElasticsearchRepository {

    /**
     * @return The name of the Elasticsearch client, {@code default} when unqualified
     */
    @AliasFor(annotation = Repository.class, member = "value")
    String value() default "default";
}

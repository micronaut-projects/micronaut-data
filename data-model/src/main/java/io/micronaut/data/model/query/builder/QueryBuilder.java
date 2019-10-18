/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.model.query.builder;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.QueryModel;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;

import java.util.List;
import java.util.regex.Pattern;

/**
 * An interface capable of encoding a query into a string and a set of named parameters.
 *
 * @author graemerocher
 * @since 1.0
 */
@Introspected
public interface QueryBuilder {

    /**
     * A pattern used to find variables in a query string.
     */
    Pattern VARIABLE_PATTERN = Pattern.compile("(:([a-zA-Z0-9]+))");

    /**
     * Builds an insert statement for the given entity.
     * @param repositoryMetadata The repository annotation metadata
     * @param entity The entity
     * @return The insert statement or null if the implementation doesn't require insert statements
     */
    @Nullable
    QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @return The encoded query
     */
    @NonNull
    default QueryResult buildQuery(@NonNull QueryModel query) {
        return buildQuery(AnnotationMetadata.EMPTY_METADATA, query);
    }

    /**
     * Encode the given query for the passed annotation metadata and query.
     * @param annotationMetadata The annotation metadata
     * @param query The query model
     * @return The query result
     */
    QueryResult buildQuery(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @param propertiesToUpdate The property names to update
     * @return The encoded query
     */
    @NonNull
    default QueryResult buildUpdate(@NonNull QueryModel query, @NonNull List<String> propertiesToUpdate) {
        return buildUpdate(AnnotationMetadata.EMPTY_METADATA, query, propertiesToUpdate);
    }

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param annotationMetadata The annotation metadata
     * @param query The query
     * @param propertiesToUpdate The property names to update
     * @return The encoded query
     */
    QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query, @NonNull List<String> propertiesToUpdate);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @return The encoded query
     */
    @NonNull
    default QueryResult buildDelete(@NonNull QueryModel query) {
        return buildDelete(AnnotationMetadata.EMPTY_METADATA, query);
    }

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param annotationMetadata The annotation metadata
     * @param query The query
     * @return The encoded query
     */
    QueryResult buildDelete(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param entity The root entity
     * @param sort The sort
     * @return The encoded query
     */
    @NonNull
    QueryResult buildOrderBy(@NonNull PersistentEntity entity, @NonNull Sort sort);

    /**
     * Encode the pageable.
     *
     * @param pageable The pageable
     * @return The encoded query
     */
    @NonNull
    QueryResult buildPagination(@NonNull Pageable pageable);

    /**
     * Build a query build from the configured annotation metadata.
     * @param annotationMetadata The annotation metadata.
     * @return The query builder
     */
    static @NonNull QueryBuilder newQueryBuilder(@NonNull AnnotationMetadata annotationMetadata) {
        return annotationMetadata.stringValue(
                RepositoryConfiguration.class,
                DataMethod.META_MEMBER_QUERY_BUILDER
        ).flatMap(type -> BeanIntrospector.SHARED.findIntrospections(ref -> ref.isPresent() && ref.getBeanType().getName().equals(type))
                .stream().findFirst()
                .map(introspection -> {
                    try {
                        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
                        if (constructorArguments.length == 0) {
                            return (QueryBuilder) introspection.instantiate();
                        } else if (constructorArguments.length == 1 && constructorArguments[0].getType() == AnnotationMetadata.class) {
                            return (QueryBuilder) introspection.instantiate(annotationMetadata);
                        }
                    } catch (InstantiationException e) {
                        return new JpaQueryBuilder();
                    }
                    return new JpaQueryBuilder();
                })).orElse(new JpaQueryBuilder());
    }
}

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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.QueryModel;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * An interface capable of encoding a query into a string and a set of named parameters.
 *
 * @author graemerocher
 * @since 1.0
 */
@Introspected
public interface QueryBuilder {

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
    QueryResult buildQuery(@NonNull QueryModel query);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @param propertiesToUpdate The property names to update
     * @return The encoded query
     */
    @NonNull
    QueryResult buildUpdate(@NonNull QueryModel query, List<String> propertiesToUpdate);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @return The encoded query
     */
    @NonNull
    QueryResult buildDelete(@NonNull QueryModel query);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param entity The root entity
     * @param sort The sort
     * @return The encoded query
     */
    @NonNull
    QueryResult buildOrderBy(@NonNull PersistentEntity entity, @NonNull Sort sort);
}

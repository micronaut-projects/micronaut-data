/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model.query.builder;

import io.micronaut.data.model.query.Query;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * An interface capable of encoding a query into a string and a set of named parameters.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface QueryBuilder {

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @return The encoded query
     */
    @NonNull
    QueryResult buildQuery(@NonNull Query query);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @param propertiesToUpdate The property names to update
     * @return The encoded query
     */
    @NonNull
    QueryResult buildUpdate(@NonNull Query query, List<String> propertiesToUpdate);

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query The query
     * @return The encoded query
     */
    @NonNull
    QueryResult buildDelete(@NonNull Query query);

    /**
     * When producing the query this dedicates whether to use the mapped names (such as the column name)
     * or the original Java property name. To encode JPA-QL for example you want to use the Java property names,
     * whilst SQL requires the raw column names.
     *
     * @return Whether to use mapped names
     */
    default boolean useMappedNames() {
        return true;
    }
}

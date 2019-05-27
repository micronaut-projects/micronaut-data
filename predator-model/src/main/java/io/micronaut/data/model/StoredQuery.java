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
package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ReflectionUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * A stored computed query. This interface represents the
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <E> The entity type
 * @param <R> The result type
 */
public interface StoredQuery<E, R> extends Named, AnnotationMetadataProvider {

    /**
     * The root entity type.
     *
     * @return The root entity type
     */
    @NonNull
    Class<E> getRootEntity();

    /**
     * The query to execute.
     *
     * @return The query to execute
     */
    @NonNull
    String getQuery();

    /**
     * The query result type. This may differ from the root entity type returned by {@link #getRootEntity()}.
     *
     * @return The query result type
     */
    @NonNull
    Class<R> getResultType();

    /**
     * In cases where one needs to differentiate between at higher level query format (like JPA-QL) and a lower level format (like SQL).
     *
     * @return Whether the query is native.
     */
    default boolean isNative() {
        return false;
    }

    /**
     * Returns whether the query returns the actual entity or a Data Transfer Object (DTO) project. Defaults to false.
     *
     * @return Whether the query is a DTO projection query
     */
    default boolean isDtoProjection() {
        return false;
    }

    /**
     * The type of the ID member of the entity.
     *
     * @return The ID type
     */
    default Optional<Class<?>> getEntityIdentifierType() {
        return Optional.empty();
    }

    /**
     * The argument types to the method that invokes the query.
     *
     * @return The argument types
     */
    @NonNull
    default Class<?>[] getArgumentTypes() {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    /**
     * The parameter binding. That is the mapping between named query parameters and parameters of the method.
     *
     * @return The parameter binding.
     */
    @NonNull
    default Map<String, String> getParameterBinding() {
        return Collections.emptyMap();
    }

    /**
     * The name of the last updated property on the entity if any
     *
     * @return The last updated property
     */
    default Optional<String> getLastUpdatedProperty() {
        return Optional.empty();
    }
}

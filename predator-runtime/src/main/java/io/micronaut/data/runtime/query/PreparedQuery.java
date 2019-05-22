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
package io.micronaut.data.runtime.query;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.data.model.Pageable;

import java.util.Map;

/**
 * Interface that models a prepared query.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <E> The entity type
 * @param <R> The result type
 */
public interface PreparedQuery<E, R> extends AnnotationMetadataProvider {

    /**
     * @return Whether the query is a DTO projection query
     */
    boolean isDtoProjection();

    /**
     * @return The query result type
     */
    @NonNull
    Class<R> getResultType();

    /**
     * @return The ID type
     */
    @Nullable
    Class<?> getEntityIdentifierType();

    /**
     * @return The root entity type
     */
    @NonNull
    Class<E> getRootEntity();

    /**
     * @return The query to execute
     */
    @NonNull
    String getQuery();

    /**
     * @return The named parameter values
     */
    @NonNull
    Map<String, Object> getParameterValues();

    /**
     * @return The pageable object
     */
    @NonNull
    Pageable getPageable();
}

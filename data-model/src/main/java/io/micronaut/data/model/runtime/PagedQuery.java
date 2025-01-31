/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.Named;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

import java.util.Collections;
import java.util.Map;

/**
 * Object passed to queries for pagination requests.
 * @param <E> The entity type
 * @since 1.0.0
 * @author graemerocher
 */
public interface PagedQuery<E> extends Named, AnnotationMetadataProvider {
    /**
     * The root entity type.
     *
     * @return The root entity type
     */
    @NonNull
    Class<E> getRootEntity();

    /**
     * @return The pageable object. Defaults to {@link Pageable#UNPAGED}
     */
    @NonNull
    Pageable getPageable();

    /**
     * @return The limit
     * @see 4.12
     */
    @NonNull
    default Limit getQueryLimit() {
        return getPageable().getLimit();
    }

    /**
     * @return The sort
     * @see 4.12
     */
    @NonNull
    default Sort getSort() {
        return getPageable().getSort();
    }

    /**
     * The parameter binding. That is the mapping between named query parameters and parameters of the method.
     *
     * @return The parameter binding.
     */
    @NonNull
    default Map<String, Object> getQueryHints() {
        return Collections.emptyMap();
    }

}

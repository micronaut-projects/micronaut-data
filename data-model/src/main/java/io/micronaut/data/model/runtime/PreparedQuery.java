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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionServiceProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Sort;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Interface that models a prepared query. A prepared query extends from {@link StoredQuery} and includes the bound parameter values.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <E> The entity type
 * @param <R> The result type
 */
public interface PreparedQuery<E, R> extends PagedQuery<E>, StoredQuery<E, R>, PreparedDataOperation<R>, ConversionServiceProvider {

    /**
     * @return The repository type.
     */
    Class<?> getRepositoryType();

    /**
     * @return The method parameters
     */
    @Nullable
    Object [] getParameterArray();

    /**
     * @return The method arguments
     */
    Argument[] getArguments();

    @Override
    default Map<String, Object> getQueryHints() {
        return Collections.emptyMap();
    }

    /**
     * Gets an indicator telling whether underlying query is raw query.
     *
     * @return true if it is raw query
     */
    @Override
    boolean isRawQuery();

    @Override
    default Sort getSort() {
        return PagedQuery.super.getSort();
    }

    @Override
    default Limit getQueryLimit() {
        return PagedQuery.super.getQueryLimit();
    }

    @Override
    default ConversionService getConversionService() {
        return ConversionService.SHARED;
    }
}

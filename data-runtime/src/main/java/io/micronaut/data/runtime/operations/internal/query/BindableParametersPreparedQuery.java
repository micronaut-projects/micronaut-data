/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.operations.internal.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;

import java.util.Map;

/**
 * Bindable parameters version of {@link PreparedQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.8.0
 */
@Internal
public interface BindableParametersPreparedQuery<E, R> extends PreparedQuery<E, R>, BindableParametersStoredQuery<E, R> {

    /**
     * Bind query parameters.
     *
     * @param binder         The binder
     * @param entity         The entity
     * @param previousValues The previous auto-populated collected values
     */
    void bindParameters(Binder binder, @Nullable E entity, @Nullable Map<QueryParameterBinding, Object> previousValues);

    /**
     * Bind query parameters.
     *
     * @param binder The binder
     */
    default void bindParameters(Binder binder) {
        bindParameters(binder, null, null);
    }

}

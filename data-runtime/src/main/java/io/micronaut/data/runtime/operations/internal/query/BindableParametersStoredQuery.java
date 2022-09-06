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

import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;

import java.util.Collection;
import java.util.Map;

/**
 * Bindable parameters version of {@link StoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.8.0
 */
@Internal
public interface BindableParametersStoredQuery<E, R> extends StoredQuery<E, R>, PersistentEntityAwareQuery<E> {

    /**
     * Bind query parameters.
     *
     * @param binder            The binder
     * @param invocationContext The invocation context
     * @param entity            The entity
     * @param previousValues    The previous auto-populated collected values
     */
    void bindParameters(Binder binder,
                        @Nullable InvocationContext<?, ?> invocationContext,
                        @Nullable E entity,
                        @Nullable Map<QueryParameterBinding, Object> previousValues);

    /**
     * Parameters binder.
     */
    interface Binder {

        /**
         * Auto populate property value.
         *
         * @param persistentProperty The property
         * @param previousValue      The previous value
         * @return The populated value
         */
        @NonNull
        Object autoPopulateRuntimeProperty(@NonNull RuntimePersistentProperty<?> persistentProperty, @Nullable Object previousValue);

        /**
         * Convert value according to the property definition.
         *
         * @param value    The value
         * @param property The property
         * @return The converted value
         */
        @Nullable
        Object convert(@Nullable Object value, @Nullable RuntimePersistentProperty<?> property);

        /**
         * Convert value using the converter class.
         *
         * @param converterClass The converterClass
         * @param value          The value
         * @param argument       The argument
         * @return The converted value
         */
        @Nullable
        Object convert(@Nullable Class<?> converterClass, @Nullable Object value, @Nullable Argument<?> argument);

        /**
         * Bind the value.
         *
         * @param binding The binding
         * @param value    The value
         */
        void bindOne(@NonNull QueryParameterBinding binding, @Nullable Object value);

        /**
         * Bind multiple values.
         *
         * @param binding The binding
         * @param values  The values
         */
        void bindMany(@NonNull QueryParameterBinding binding, @NonNull Collection<Object> values);

        /**
         * @return current index
         */
        default int currentIndex() {
            return -1;
        }

    }
}

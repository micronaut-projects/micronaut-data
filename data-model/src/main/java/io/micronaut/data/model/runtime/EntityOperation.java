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

import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;

/**
 * An operation on an entity type.
 * @param <E> The entity type
 */
public interface EntityOperation<E> extends Named, PreparedDataOperation<E> {
    /**
     * The root entity type.
     *
     * @return The root entity type
     */
    @NonNull
    Class<E> getRootEntity();

    /**
     * @return The repository type.
     */
    @NonNull
    Class<?> getRepositoryType();

    /**
     * Possible stored query if exists.
     * @return The stored query
     */
    @Nullable
    StoredQuery<E, ?> getStoredQuery();

    /**
     * @return The invocation context
     */
    @Nullable
    InvocationContext<?, ?> getInvocationContext();
}

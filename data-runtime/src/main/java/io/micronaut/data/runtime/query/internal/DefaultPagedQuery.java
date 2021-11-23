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
package io.micronaut.data.runtime.query.internal;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.inject.ExecutableMethod;

/**
 * Default implementation of {@link PagedQuery}.
 *
 * @param <E> The paged query
 */
@Internal
public final class DefaultPagedQuery<E> implements PagedQuery<E> {

    private final ExecutableMethod<?, ?> method;
    private final @NonNull
    Class<E> rootEntity;
    private final Pageable pageable;

    /**
     * Default constructor.
     *
     * @param method     The method
     * @param rootEntity The root entity
     * @param pageable   The pageable
     */
    public DefaultPagedQuery(ExecutableMethod<?, ?> method, @NonNull Class<E> rootEntity, Pageable pageable) {
        this.method = method;
        this.rootEntity = rootEntity;
        this.pageable = pageable;
    }

    @NonNull
    @Override
    public Class<E> getRootEntity() {
        return rootEntity;
    }

    @NonNull
    @Override
    public Pageable getPageable() {
        return pageable;
    }

    @NonNull
    @Override
    public String getName() {
        return method.getMethodName();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return method.getAnnotationMetadata();
    }
}

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
package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.FindCursoredPageInterceptor;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Default implementation of {@link FindCursoredPageInterceptor}.
 *
 * @param <T> The declaring type
 * @param <R> The paged type.
 * @author Andriy Dmytruk
 * @since 4.8.0
 */
public class DefaultFindCursoredPageInterceptor<T, R> extends DefaultFindPageInterceptor<T, R> implements FindCursoredPageInterceptor<T, R> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultFindCursoredPageInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    protected Pageable getPageable(MethodInvocationContext<?, ?> context) {
        Pageable pageable = super.getPageable(context);
        if (pageable.getMode() == Mode.OFFSET) {
            if (pageable.getNumber() == 0) {
                pageable = CursoredPageable.from(pageable.getSize(), pageable.getSort());
            } else {
                throw new IllegalArgumentException("Pageable with offset mode provided, but method must return a cursored page");
            }
        }
        return pageable;
    }
}

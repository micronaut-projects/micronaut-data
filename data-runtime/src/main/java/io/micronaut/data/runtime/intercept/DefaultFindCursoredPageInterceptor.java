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

import org.jspecify.annotations.NonNull;
import io.micronaut.data.intercept.FindCursoredPageInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

/**
 * Default implementation of {@link FindCursoredPageInterceptor}.
 *
 * @param <T> The declaring type
 * @param <R> The paged type.
 * @author Andriy Dmytruk
 * @since 4.8.0
 */
public class DefaultFindCursoredPageInterceptor<T, R> extends DefaultAbstractFindPageInterceptor<T, R> implements FindCursoredPageInterceptor<T, R> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultFindCursoredPageInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

}

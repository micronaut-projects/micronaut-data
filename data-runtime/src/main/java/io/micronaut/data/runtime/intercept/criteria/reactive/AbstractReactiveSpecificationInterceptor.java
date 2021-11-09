/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.intercept.criteria.reactive;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.intercept.criteria.AbstractSpecificationInterceptor;

/**
 * Abstract reactive specification interceptor.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 3.2
 */
public abstract class AbstractReactiveSpecificationInterceptor<T, R> extends AbstractSpecificationInterceptor<T, R> {

    protected final ReactiveRepositoryOperations reactiveOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected AbstractReactiveSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
        if (operations instanceof ReactiveCapableRepository) {
            this.reactiveOperations = ((ReactiveCapableRepository) operations).reactive();
        } else {
            throw new DataAccessException("Datastore of type [" + operations.getClass() + "] does not support reactive operations");
        }
    }
}

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
package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;

import java.util.concurrent.CompletionStage;

/**
 * Abstract asynchronous interceptor implementation.
 * @param <T> The declaring type
 * @param <R> The result type.
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class AbstractAsyncInterceptor<T, R> extends AbstractQueryInterceptor<T, CompletionStage<R>> {

    @NonNull
    protected final AsyncRepositoryOperations asyncDatastoreOperations;

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected AbstractAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
        if (datastore instanceof AsyncCapableRepository) {
            this.asyncDatastoreOperations = ((AsyncCapableRepository) datastore).async();
        } else {
            throw new DataAccessException("Datastore of type [" + datastore.getClass() + "] does not support asynchronous operations");
        }
    }

}

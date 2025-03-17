/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.runtime.DeleteReturningBatchOperation;
import io.micronaut.data.model.runtime.DeleteReturningOperation;

import java.util.List;

/**
 * A variation of {@link RepositoryOperations} that supports delete returning operations.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Experimental
public interface DeleteReturningRepositoryOperations extends RepositoryOperations {

    /**
     * Deletes the entity and returns a result.
     *
     * @param operation The operation
     * @param <E>       The entity type
     * @param <R>       The result type
     * @return The deleted entity
     */
    <E, R> R deleteReturning(@NonNull DeleteReturningOperation<E, R> operation);

    /**
     * Deletes the entities and returns a result.
     *
     * @param operation The operation
     * @param <E>       The entity type
     * @param <R>       The result type
     * @return The deleted entities
     */
    <E, R> List<R> deleteAllReturning(@NonNull DeleteReturningBatchOperation<E, R> operation);
}

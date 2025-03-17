/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.runtime.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.operations.reactive.ReactiveCriteriaRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import org.reactivestreams.Publisher;

/**
 * A variation of {@link ExecutorReactiveOperations} that supports {@link ReactiveCriteriaRepositoryOperations}.
 * @author Denis Stepanov
 */
@Experimental
public class ExecutorReactiveOperationsSupportingCriteria extends ExecutorReactiveOperations implements ReactiveCriteriaRepositoryOperations {

    private final ExecutorAsyncOperationsSupportingCriteria asyncOperations;

    public ExecutorReactiveOperationsSupportingCriteria(ExecutorAsyncOperationsSupportingCriteria asyncOperations,
                                                        DataConversionService dataConversionService) {
        super(asyncOperations, dataConversionService);
        this.asyncOperations = asyncOperations;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return asyncOperations.getCriteriaBuilder();
    }

    @Override
    public Publisher<Boolean> exists(CriteriaQuery<?> query) {
        return fromCompletableFuture(() -> asyncOperations.exists(query).toCompletableFuture());
    }

    @Override
    public <R> Publisher<R> findOne(CriteriaQuery<R> query) {
        return fromCompletableFuture(() -> asyncOperations.findOne(query).toCompletableFuture());
    }

    @Override
    public <T> Publisher<T> findAll(CriteriaQuery<T> query) {
        return fromCompletableFuture(() -> asyncOperations.findAll(query).toCompletableFuture()).flatMapIterable(list -> list);
    }

    @Override
    public <T> Publisher<T> findAll(CriteriaQuery<T> query, int offset, int limit) {
        return fromCompletableFuture(() -> asyncOperations.findAll(query, offset, limit).toCompletableFuture()).flatMapIterable(list -> list);
    }

    @Override
    public Publisher<Number> updateAll(CriteriaUpdate<Number> query) {
        return fromCompletableFuture(() -> asyncOperations.updateAll(query).toCompletableFuture());
    }

    @Override
    public Publisher<Number> deleteAll(CriteriaDelete<Number> query) {
        return fromCompletableFuture(() -> asyncOperations.deleteAll(query).toCompletableFuture());
    }
}

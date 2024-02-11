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
package io.micronaut.data.operations.reactive;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.operations.BlockingCriteriaCapableRepository;
import io.micronaut.data.operations.CriteriaRepositoryOperations;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link CriteriaRepositoryOperations} that blocks every call from {@link ReactorCriteriaCapableRepository}.
 *
 * @author Denis Stepanov
 * @since 4.5.0
 */
@Experimental
public interface BlockingReactorCriteriaRepositoryOperations extends CriteriaRepositoryOperations,
    BlockingCriteriaCapableRepository,
    ReactorCriteriaCapableRepository {

    @Override
    default CriteriaRepositoryOperations blocking() {
        return this;
    }

    @NonNull
    private ContextView getContextView() {
        return ReactorPropagation.addPropagatedContext(Context.empty(), PropagatedContext.getOrEmpty());
    }

    @Override
    @Nullable
    default <R> R findOne(@NonNull CriteriaQuery<R> query) {
        return reactive().findOne(query)
            .contextWrite(getContextView())
            .block();
    }

    @Override
    default <T> List<T> findAll(@NonNull CriteriaQuery<T> query) {
        return reactive().findAll(query)
            .collectList()
            .contextWrite(getContextView())
            .blockOptional()
            .orElseGet(List::of);
    }

    @Override
    default <T> List<T> findAll(@NonNull CriteriaQuery<T> query, int limit, int offset) {
        return reactive().findAll(query, limit, offset)
            .collectList()
            .contextWrite(getContextView())
            .blockOptional()
            .orElseGet(List::of);
    }

    @Override
    default Optional<Number> updateAll(@NonNull CriteriaUpdate<Number> query) {
        return reactive().updateAll(query)
            .contextWrite(getContextView())
            .blockOptional();
    }

    @Override
    default Optional<Number> deleteAll(@NonNull CriteriaDelete<Number> query) {
        return reactive().deleteAll(query)
            .contextWrite(getContextView())
            .blockOptional();
    }

}


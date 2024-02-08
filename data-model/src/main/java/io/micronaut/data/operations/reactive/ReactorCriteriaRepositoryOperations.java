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
package io.micronaut.data.operations.reactive;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The repository operations that support executing criteria queries.
 *
 * @author Denis Stepanov
 * @since 4.5.0
 */
@Experimental
public interface ReactorCriteriaRepositoryOperations extends ReactiveCriteriaRepositoryOperations {

    @Override
    <R> Mono<R> findOne(@NonNull CriteriaQuery<R> query);

    @Override
    <T> Flux<T> findAll(@NonNull CriteriaQuery<T> query);

    @Override
    Mono<Number> updateAll(@NonNull CriteriaUpdate<Number> query);

    @Override
    Mono<Number> deleteAll(@NonNull CriteriaDelete<Number> query);

}

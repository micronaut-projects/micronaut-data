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
package io.micronaut.data.repository.jpa.reactive;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface representing Reactor version of {@link io.micronaut.data.repository.jpa.JpaSpecificationExecutor}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
public interface ReactorJpaSpecificationExecutor<T> extends ReactiveStreamsJpaSpecificationExecutor<T> {

    @Override
    @NonNull
    Mono<T> findOne(@Nullable QuerySpecification<T> spec);

    @Override
    @NonNull
    Mono<T> findOne(@Nullable PredicateSpecification<T> spec);

    @Override
    @NonNull
    Flux<T> findAll(@Nullable QuerySpecification<T> spec);

    @Override
    @NonNull
    Flux<T> findAll(@Nullable PredicateSpecification<T> spec);

    @Override
    Mono<Page<T>> findAll(QuerySpecification<T> spec, Pageable pageable);

    @Override
    Mono<Page<T>> findAll(PredicateSpecification<T> spec, Pageable pageable);

    @Override
    @NonNull
    Flux<T> findAll(@Nullable QuerySpecification<T> spec, Sort sort);

    @Override
    @NonNull
    Flux<T> findAll(@Nullable PredicateSpecification<T> spec, Sort sort);

    @Override
    @NonNull
    Mono<Long> count(@Nullable QuerySpecification<T> spec);

    @Override
    @NonNull
    Mono<Long> count(@Nullable PredicateSpecification<T> spec);

    @Override
    @NonNull
    Mono<Boolean> exists(QuerySpecification<T> spec);

    @Override
    @NonNull
    Mono<Boolean> exists(PredicateSpecification<T> spec);

    @Override
    @NonNull
    Mono<Long> deleteAll(@Nullable DeleteSpecification<T> spec);

    @Override
    @NonNull
    Mono<Long> deleteAll(@Nullable PredicateSpecification<T> spec);

    @Override
    @NonNull
    Mono<Long> updateAll(@Nullable UpdateSpecification<T> spec);

}

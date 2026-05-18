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

import org.jspecify.annotations.Nullable;
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
    Mono<T> findOne(@Nullable @io.micronaut.core.annotation.Nullable QuerySpecification<T> spec);

    @Override
    Mono<T> findOne(@Nullable @io.micronaut.core.annotation.Nullable PredicateSpecification<T> spec);

    @Override
    Flux<T> findAll(@Nullable @io.micronaut.core.annotation.Nullable QuerySpecification<T> spec);

    @Override
    Flux<T> findAll(@Nullable @io.micronaut.core.annotation.Nullable PredicateSpecification<T> spec);

    @Override
    Mono<Page<T>> findAll(@Nullable @io.micronaut.core.annotation.Nullable QuerySpecification<T> spec, Pageable pageable);

    @Override
    Mono<Page<T>> findAll(@Nullable @io.micronaut.core.annotation.Nullable PredicateSpecification<T> spec, Pageable pageable);

    @Override
    Flux<T> findAll(@Nullable @io.micronaut.core.annotation.Nullable QuerySpecification<T> spec, Sort sort);

    @Override
    Flux<T> findAll(@Nullable @io.micronaut.core.annotation.Nullable PredicateSpecification<T> spec, Sort sort);

    @Override
    Mono<Long> count(@Nullable @io.micronaut.core.annotation.Nullable QuerySpecification<T> spec);

    @Override
    Mono<Long> count(@Nullable @io.micronaut.core.annotation.Nullable PredicateSpecification<T> spec);

    @Override
    Mono<Boolean> exists(@Nullable @io.micronaut.core.annotation.Nullable QuerySpecification<T> spec);

    @Override
    Mono<Boolean> exists(@Nullable @io.micronaut.core.annotation.Nullable PredicateSpecification<T> spec);

    @Override
    Mono<Long> deleteAll(@Nullable @io.micronaut.core.annotation.Nullable DeleteSpecification<T> spec);

    @Override
    Mono<Long> deleteAll(@Nullable @io.micronaut.core.annotation.Nullable PredicateSpecification<T> spec);

    @Override
    Mono<Long> updateAll(@Nullable @io.micronaut.core.annotation.Nullable UpdateSpecification<T> spec);

}

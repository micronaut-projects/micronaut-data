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
package io.micronaut.data.repository.reactive;

import io.micronaut.core.annotation.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * CRUD repository for Project Reactor.
 * @param <E> The entity type
 * @param <ID> The ID type
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 3.1
 * @see ReactiveStreamsCrudRepository
 */
public interface ReactorCrudRepository<E, ID> extends ReactiveStreamsCrudRepository<E, ID> {
    @NonNull
    @Override
    <S extends E> Mono<S> save(@NonNull @Valid @NotNull S entity);

    @NonNull
    @Override
    <S extends E> Flux<S> saveAll(@NonNull @Valid @NotNull Iterable<S> entities);

    @NonNull
    @Override
    <S extends E> Mono<S> update(@NonNull @Valid @NotNull S entity);

    @NonNull
    @Override
    <S extends E> Flux<S> updateAll(@NonNull @Valid @NotNull Iterable<S> entities);

    @NonNull
    @Override
    Mono<E> findById(@NonNull @NotNull ID id);

    @NonNull
    @Override
    Mono<Boolean> existsById(@NonNull @NotNull ID id);

    @NonNull
    @Override
    Flux<E> findAll();

    @NonNull
    @Override
    Mono<Long> count();

    @NonNull
    @Override
    Mono<Long> deleteById(@NonNull @NotNull ID id);

    @NonNull
    @Override
    Mono<Long> delete(@NonNull @NotNull E entity);

    @NonNull
    @Override
    Mono<Long> deleteAll(@NonNull @NotNull Iterable<? extends E> entities);

    @NonNull
    @Override
    Mono<Long> deleteAll();
}

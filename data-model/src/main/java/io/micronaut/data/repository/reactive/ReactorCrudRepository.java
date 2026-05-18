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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    
    @Override
    <S extends E> Mono<S> save(S entity);

    @Override
    <S extends E> Mono<S> insert(S entity);

    @Override
    <S extends E> Flux<S> saveAll(Iterable<S> entities);

    @Override
    <S extends E> Flux<S> insertAll(Iterable<S> entities);

    @Override
    <S extends E> Mono<S> update(S entity);

    @Override
    <S extends E> Flux<S> updateAll(Iterable<S> entities);

    @Override
    Mono<E> findById(ID id);

    @Override
    Mono<Boolean> existsById(ID id);

    @Override
    Flux<E> findAll();

    @Override
    Mono<Long> count();

    @Override
    Mono<Long> deleteById(ID id);

    @Override
    Mono<Long> delete(E entity);

    @Override
    Mono<Long> deleteAll(Iterable<? extends E> entities);

    @Override
    Mono<Long> deleteAll();
}

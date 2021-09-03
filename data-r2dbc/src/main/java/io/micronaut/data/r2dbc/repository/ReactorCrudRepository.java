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
package io.micronaut.data.r2dbc.repository;

import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;

/**
 * CRUD repository for Project Reactor.
 * @param <E> The entity type
 * @param <ID> The ID type
 * @see ReactiveStreamsCrudRepository
 * @deprecated Replaced by {@link io.micronaut.data.repository.reactive.ReactorCrudRepository}
 */
@Deprecated
public interface ReactorCrudRepository<E, ID> extends io.micronaut.data.repository.reactive.ReactorCrudRepository<E, ID> {
}

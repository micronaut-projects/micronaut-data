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
package io.micronaut.data.repository;

import io.micronaut.core.annotation.Indexed;

/**
 * Parent repository interface for all repositories.
 * <p>
 * {@code GenericRepository} declares no methods of its own. Micronaut Data implements the query methods declared on
 * an interface that extends it (for example {@code findByTitle(String title)}) at compile time, using the type
 * arguments to identify the root entity and its ID type. Extend {@link CrudRepository} or one of its variants to also
 * inherit the standard create, read, update and delete operations.
 * <p>
 * Repository methods report datastore errors with subclasses of
 * {@link io.micronaut.data.exceptions.DataAccessException}, as described in {@link CrudRepository}. In particular,
 * a query method whose return type is neither {@link java.util.Optional} nor nullable throws
 * {@link io.micronaut.data.exceptions.EmptyResultException} when no result is found.
 *
 * @author graemerocher
 * @since 1.0
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 */
@Indexed(GenericRepository.class)
public interface GenericRepository<E, ID> {
}

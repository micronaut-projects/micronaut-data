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
package io.micronaut.data.jpa.repository;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.jpa.repository.intercept.FlushInterceptor;
import io.micronaut.data.jpa.repository.intercept.LoadInterceptor;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.PageableRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * Simple composed repository interface that includes {@link CrudRepository} and {@link PageableRepository}.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 * @since 1.0.0
 * @author graemerocher
 */
public interface JpaRepository<E, ID> extends CrudRepository<E, ID>, PageableRepository<E, ID> {
    @NonNull
    @Override
    <S extends E> List<S> saveAll(@NonNull @Valid @NotNull Iterable<S> entities);

    @NonNull
    @Override
    List<E> findAll();

    @NonNull
    @Override
    List<E> findAll(@NonNull Sort sort);

    /**
     * Save and perform a flush() of any pending operations.
     * @param entity The entity
     * @param <S> The entity generic type
     * @return The entity, possibly mutated
     */
    @QueryHint(name = "jakarta.persistence.FlushModeType", value = "AUTO")
    <S extends E> S saveAndFlush(@NonNull @Valid @NotNull S entity);

    /**
     * Adds a flush method.
     */
    @DataMethod(interceptor = FlushInterceptor.class)
    void flush();

    /**
     * Adds a load method.
     * @param id the entity ID
     * @param <S> the entity type
     * @return An uninitialized proxy
     */
    @DataMethod(interceptor = LoadInterceptor.class)
    <S extends E> S load(@NonNull @NotNull Serializable id);
}

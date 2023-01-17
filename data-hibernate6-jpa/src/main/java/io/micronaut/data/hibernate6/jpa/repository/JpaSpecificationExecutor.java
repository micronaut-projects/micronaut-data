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
package io.micronaut.data.hibernate6.jpa.repository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.hibernate6.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

import java.util.List;
import java.util.Optional;

/**
 * Interface to allow execution of {@link Specification}s based on the JPA criteria API.
 *
 * Note: Forked from 'org.springframework.data.jpa.repository.JpaSpecificationExecutor'.
 *
 * @param <T> The entity type
 * @author Oliver Gierke
 * @author Christoph Strobl
 * @since 3.1
 */
public interface JpaSpecificationExecutor<T> {

    /**
     * Returns a single entity matching the given {@link Specification} or {@link Optional#empty()} if none found.
     *
     * @param spec can be {@literal null}.
     * @return never {@literal null}.
     */
    Optional<T> findOne(@Nullable Specification<T> spec);

    /**
     * Returns all entities matching the given {@link Specification}.
     *
     * @param spec can be {@literal null}.
     * @return never {@literal null}.
     */
    List<T> findAll(@Nullable Specification<T> spec);

    /**
     * Returns a {@link Page} of entities matching the given {@link Specification}.
     *
     * @param spec can be {@literal null}.
     * @param pageable must not be {@literal null}.
     * @return never {@literal null}.
     */
    Page<T> findAll(@Nullable Specification<T> spec, Pageable pageable);

    /**
     * Returns all entities matching the given {@link Specification} and {@link Sort}.
     *
     * @param spec can be {@literal null}.
     * @param sort must not be {@literal null}.
     * @return never {@literal null}.
     */
    List<T> findAll(@Nullable Specification<T> spec, Sort sort);

    /**
     * Returns the number of instances that the given {@link Specification} will return.
     *
     * @param spec the {@link Specification} to count instances for. Can be {@literal null}.
     * @return the number of instances.
     */
    long count(@Nullable Specification<T> spec);
}

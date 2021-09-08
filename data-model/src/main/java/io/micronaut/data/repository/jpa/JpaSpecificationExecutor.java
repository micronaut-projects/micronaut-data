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
package io.micronaut.data.repository.jpa;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification;

import java.util.List;
import java.util.Optional;

/**
 * Interface to allow execution of query/delete/update methods using dynamic JPA criteria API.
 *
 * Based on Spring Data's 'org.springframework.data.jpa.repository.JpaSpecificationExecutor'.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
public interface JpaSpecificationExecutor<T> {

    /**
     * Returns a single entity matching the given {@link QuerySpecification}.
     *
     * @param spec The query specification
     * @return optional found result
     */
    Optional<T> findOne(@Nullable QuerySpecification<T> spec);

    /**
     * Returns a single entity matching the given {@link PredicateSpecification}.
     *
     * @param spec The query specification
     * @return optional found result
     */
    Optional<T> findOne(@Nullable PredicateSpecification<T> spec);

    /**
     * Returns all entities matching the given {@link QuerySpecification}.
     *
     * @param spec The query specification
     * @return found results
     */
    @NonNull
    List<T> findAll(@Nullable QuerySpecification<T> spec);

    /**
     * Returns all entities matching the given {@link PredicateSpecification}.
     *
     * @param spec The query specification
     * @return found results
     */
    @NonNull
    List<T> findAll(@Nullable PredicateSpecification<T> spec);

    /**
     * Returns a {@link Page} of entities matching the given {@link QuerySpecification}.
     *
     * @param spec     The query specification
     * @param pageable The pageable object
     * @return a page
     */
    @NonNull
    Page<T> findAll(@Nullable QuerySpecification<T> spec, Pageable pageable);

    /**
     * Returns a {@link Page} of entities matching the given {@link QuerySpecification}.
     *
     * @param spec     The query specification
     * @param pageable The pageable object
     * @return a page
     */
    @NonNull
    Page<T> findAll(@Nullable PredicateSpecification<T> spec, Pageable pageable);

    /**
     * Returns all entities matching the given {@link QuerySpecification} and {@link Sort}.
     *
     * @param spec The query specification
     * @param sort The sort object
     * @return found results
     */
    @NonNull
    List<T> findAll(@Nullable QuerySpecification<T> spec, Sort sort);

    /**
     * Returns all entities matching the given {@link QuerySpecification} and {@link Sort}.
     *
     * @param spec The query specification
     * @param sort The sort object
     * @return found results
     */
    @NonNull
    List<T> findAll(@Nullable PredicateSpecification<T> spec, Sort sort);

    /**
     * Returns the number of instances that the given {@link QuerySpecification} will return.
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    long count(@Nullable QuerySpecification<T> spec);

    /**
     * Returns the number of instances that the given {@link QuerySpecification} will return.
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    long count(@Nullable PredicateSpecification<T> spec);

    /**
     * Deletes all entities matching the given {@link DeleteSpecification}.
     *
     * @param spec The delete specification
     * @return the number records deleted.
     */
    long deleteAll(@Nullable DeleteSpecification<T> spec);

    /**
     * Deletes all entities matching the given {@link PredicateSpecification}.
     *
     * @param spec The delete specification
     * @return the number records deleted.
     */
    long deleteAll(@Nullable PredicateSpecification<T> spec);

    /**
     * Updates all entities matching the given {@link UpdateSpecification}.
     *
     * @param spec The update specification
     * @return the number records updated.
     */
    long updateAll(@Nullable UpdateSpecification<T> spec);

}

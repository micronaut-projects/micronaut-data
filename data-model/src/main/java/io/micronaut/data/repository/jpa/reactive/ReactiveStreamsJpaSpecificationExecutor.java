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
import org.reactivestreams.Publisher;

/**
 * Interface representing reactive streams version of {@link io.micronaut.data.repository.jpa.JpaSpecificationExecutor}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
public interface ReactiveStreamsJpaSpecificationExecutor<T> {

    /**
     * Returns a single entity matching the given {@link QuerySpecification}.
     *
     * @param spec The query specification
     * @return optional found result
     */
    @NonNull
    Publisher<T> findOne(@Nullable QuerySpecification<T> spec);

    /**
     * Returns a single entity matching the given {@link PredicateSpecification}.
     *
     * @param spec The query specification
     * @return optional found result
     */
    @NonNull
    Publisher<T> findOne(@Nullable PredicateSpecification<T> spec);

    /**
     * Returns all entities matching the given {@link QuerySpecification}.
     *
     * @param spec The query specification
     * @return found results
     */
    @NonNull
    Publisher<T> findAll(@Nullable QuerySpecification<T> spec);

    /**
     * Returns all entities matching the given {@link PredicateSpecification}.
     *
     * @param spec The query specification
     * @return found results
     */
    @NonNull
    Publisher<T> findAll(@Nullable PredicateSpecification<T> spec);

    /**
     * Returns a {@link Page} of entities matching the given {@link QuerySpecification}.
     *
     * @param spec     The query specification
     * @param pageable The pageable object
     * @return a page
     */
    @NonNull
    Publisher<Page<T>> findAll(@Nullable QuerySpecification<T> spec, Pageable pageable);

    /**
     * Returns a {@link Page} of entities matching the given {@link PredicateSpecification}.
     *
     * @param spec     The query specification
     * @param pageable The pageable object
     * @return a page
     */
    @NonNull
    Publisher<Page<T>> findAll(@Nullable PredicateSpecification<T> spec, Pageable pageable);

    /**
     * Returns all entities matching the given {@link QuerySpecification} and {@link Sort}.
     *
     * @param spec The query specification
     * @param sort The sort object
     * @return found results
     */
    @NonNull
    Publisher<T> findAll(@Nullable QuerySpecification<T> spec, Sort sort);

    /**
     * Returns all entities matching the given {@link QuerySpecification} and {@link Sort}.
     *
     * @param spec The query specification
     * @param sort The sort object
     * @return found results
     */
    @NonNull
    Publisher<T> findAll(@Nullable PredicateSpecification<T> spec, Sort sort);

    /**
     * Returns the number of instances that the given {@link QuerySpecification} will return.
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    @NonNull
    Publisher<Long> count(@Nullable QuerySpecification<T> spec);

    /**
     * Returns the number of instances that the given {@link QuerySpecification} will return.
     *
     * @param spec The query specification
     * @return the number of instances.
     */
    @NonNull
    Publisher<Long> count(@Nullable PredicateSpecification<T> spec);

    /**
     * Returns whether an instance was found for the given {@link QuerySpecification}.
     *
     * @param spec The query specification
     * @return the number of instances.
     * @since 3.8
     */
    @NonNull
    Publisher<Boolean> exists(@Nullable QuerySpecification<T> spec);

    /**
     * Returns whether an instance was found for the given {@link PredicateSpecification}.
     *
     * @param spec The query specification
     * @return the number of instances.
     * @since 3.8
     */
    @NonNull
    Publisher<Boolean> exists(@Nullable PredicateSpecification<T> spec);

    /**
     * Deletes all entities matching the given {@link DeleteSpecification}.
     *
     * @param spec The delete specification
     * @return the number records deleted.
     */
    @NonNull
    Publisher<Long> deleteAll(@Nullable DeleteSpecification<T> spec);

    /**
     * Deletes all entities matching the given {@link PredicateSpecification}.
     *
     * @param spec The delete specification
     * @return the number records deleted.
     */
    @NonNull
    Publisher<Long> deleteAll(@Nullable PredicateSpecification<T> spec);

    /**
     * Updates all entities matching the given {@link UpdateSpecification}.
     *
     * @param spec The update specification
     * @return the number records updated.
     */
    @NonNull
    Publisher<Long> updateAll(@Nullable UpdateSpecification<T> spec);

}

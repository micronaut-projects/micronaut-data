/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.PersistentEntity;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.Order;

import java.util.List;

/**
 * The common persistent entity query. (ordinary + subquery)
 *
 * @param <T> The type of the result
 * @author Denis Stepanov
 * @since 4.10
 */
@Experimental
public interface PersistentEntityQuery<T> extends AbstractQuery<T>, PersistentEntityCommonAbstractCriteria {

    /**
     * Create a root using {@link PersistentEntity}.
     *
     * @param persistentEntity The persistent entity
     * @param <X>              The root type
     * @return The root
     */
    @NonNull
    <X> PersistentEntityRoot<X> from(@NonNull PersistentEntity persistentEntity);

    /**
     * Sets the limit to the query.
     *
     * @param limit The limit
     * @return The query
     */
    @NonNull
    PersistentEntityQuery<T> limit(int limit);

    /**
     * Sets the offset to the query.
     *
     * @param offset The offset
     * @return The query
     */
    @NonNull
    PersistentEntityQuery<T> offset(int offset);

    /**
     * Ordering of the query.
     *
     * @param orders The order
     * @return The query
     */
    @NonNull
    PersistentEntityQuery<T> orderBy(@NonNull Order... orders);

    /**
     * Ordering of the query.
     *
     * @param orders The order
     * @return The query
     */
    @NonNull
    PersistentEntityQuery<T> orderBy(@NonNull List<Order> orders);

}

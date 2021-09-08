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
package io.micronaut.data.model.jpa.criteria;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.PersistentEntity;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;

import java.util.List;

/**
 * The persistent entity {@link CriteriaQuery}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentEntityCriteriaQuery<T> extends CriteriaQuery<T> {

    @NonNull
    <X> PersistentEntityRoot<X> from(@NonNull PersistentEntity persistentEntity);

    @Override
    @NonNull
    <X> PersistentEntityRoot<X> from(@NonNull Class<X> entityClass);

    @Override
    @NonNull
    <X> PersistentEntityRoot<X> from(@NonNull EntityType<X> entity);

    @NonNull
    PersistentEntityCriteriaQuery<T> max(int max);

    @NonNull
    PersistentEntityCriteriaQuery<T> offset(int offset);

    @Internal
    @NonNull
    default PersistentEntityCriteriaQuery<T> forUpdate(boolean forUpdate) {
        return this;
    }

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> select(@NonNull Selection<? extends T> selection);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> multiselect(@NonNull Selection<?>... selections);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> multiselect(@NonNull List<Selection<?>> selectionList);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> where(@NonNull Expression<Boolean> restriction);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> where(@NonNull Predicate... restrictions);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> groupBy(@NonNull Expression<?>... grouping);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> groupBy(@NonNull List<Expression<?>> grouping);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> having(@NonNull Expression<Boolean> restriction);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> having(@NonNull Predicate... restrictions);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> orderBy(@NonNull Order... o);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> orderBy(@NonNull List<Order> o);

    @Override
    @NonNull
    PersistentEntityCriteriaQuery<T> distinct(boolean distinct);

}

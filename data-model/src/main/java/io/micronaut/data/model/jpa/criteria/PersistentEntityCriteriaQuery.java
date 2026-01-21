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
public interface PersistentEntityCriteriaQuery<T> extends CriteriaQuery<T>, PersistentEntityQuery<T> {

    @Override
    <X> PersistentEntityRoot<X> from(PersistentEntity persistentEntity);

    @Override
    <X> PersistentEntityRoot<X> from(Class<X> entityClass);

    @Override
    <X> PersistentEntityRoot<X> from(EntityType<X> entity);

    @Override
    PersistentEntityCriteriaQuery<T> limit(int limit);

    @Override
    PersistentEntityCriteriaQuery<T> offset(int offset);

    @Internal
    default PersistentEntityCriteriaQuery<T> forUpdate(boolean forUpdate) {
        return this;
    }

    @Override
    PersistentEntityCriteriaQuery<T> select(Selection<? extends T> selection);

    @Override
    PersistentEntityCriteriaQuery<T> multiselect(Selection<?>... selections);

    @Override
    PersistentEntityCriteriaQuery<T> multiselect(List<Selection<?>> selectionList);

    @Override
    PersistentEntityCriteriaQuery<T> where(Expression<Boolean> restriction);

    @Override
    PersistentEntityCriteriaQuery<T> where(Predicate... restrictions);

    @Override
    PersistentEntityCriteriaQuery<T> groupBy(Expression<?>... grouping);

    @Override
    PersistentEntityCriteriaQuery<T> groupBy(List<Expression<?>> grouping);

    @Override
    PersistentEntityCriteriaQuery<T> having(Expression<Boolean> restriction);

    @Override
    PersistentEntityCriteriaQuery<T> having(Predicate... restrictions);

    @Override
    PersistentEntityCriteriaQuery<T> orderBy(Order... orders);

    @Override
    PersistentEntityCriteriaQuery<T> orderBy(List<Order> orders);

    @Override
    PersistentEntityCriteriaQuery<T> distinct(boolean distinct);

}

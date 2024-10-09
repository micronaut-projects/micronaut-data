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
import io.micronaut.core.annotation.NonNull;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.EntityType;

import java.util.List;

/**
 * The persistent entity {@link Subquery}.
 *
 * @param <T> The type of the selected item
 * @author Denis Stepanov
 * @since 4.10
 */
@Experimental
public interface PersistentEntitySubquery<T> extends Subquery<T>, PersistentEntityQuery<T> {

    @Override
    @NonNull
    PersistentEntitySubquery<T> limit(int limit);

    @Override
    @NonNull
    PersistentEntitySubquery<T> offset(int offset);

    @Override
    @NonNull
    <X> PersistentEntityRoot<X> from(@NonNull Class<X> entityClass);

    @Override
    @NonNull
    <X> PersistentEntityRoot<X> from(@NonNull EntityType<X> entity);

    @Override
    PersistentEntitySubquery<T> select(@NonNull Expression<T> expression);

    @Override
    @NonNull
    PersistentEntitySubquery<T> where(@NonNull Expression<Boolean> restriction);

    @Override
    @NonNull
    PersistentEntitySubquery<T> where(@NonNull Predicate... restrictions);

    @Override
    @NonNull
    PersistentEntitySubquery<T> groupBy(@NonNull Expression<?>... grouping);

    @Override
    @NonNull
    PersistentEntitySubquery<T> groupBy(@NonNull List<Expression<?>> grouping);

    @Override
    @NonNull
    PersistentEntitySubquery<T> having(@NonNull Expression<Boolean> restriction);

    @Override
    @NonNull
    PersistentEntitySubquery<T> having(@NonNull Predicate... restrictions);

    @Override
    @NonNull
    PersistentEntitySubquery<T> distinct(boolean distinct);

    /**
     * @return The expression type
     */
    ExpressionType<T> getExpressionType();

}

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
    
    PersistentEntitySubquery<T> limit(int limit);

    @Override
    
    PersistentEntitySubquery<T> offset(int offset);

    @Override
    
    <X> PersistentEntityRoot<X> from(Class<X> entityClass);

    @Override
    
    <X> PersistentEntityRoot<X> from(EntityType<X> entity);

    @Override
    PersistentEntitySubquery<T> select(Expression<T> expression);

    @Override
    
    PersistentEntitySubquery<T> where(Expression<Boolean> restriction);

    @Override
    
    PersistentEntitySubquery<T> where(Predicate... restrictions);

    @Override
    
    PersistentEntitySubquery<T> groupBy(Expression<?>... grouping);

    @Override
    
    PersistentEntitySubquery<T> groupBy(List<Expression<?>> grouping);

    @Override
    
    PersistentEntitySubquery<T> having(Expression<Boolean> restriction);

    @Override
    
    PersistentEntitySubquery<T> having(Predicate... restrictions);

    @Override
    
    PersistentEntitySubquery<T> distinct(boolean distinct);

    /**
     * @return The expression type
     */
    ExpressionType<T> getExpressionType();

}

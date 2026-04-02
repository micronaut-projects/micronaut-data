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
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.PersistentEntity;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.List;
import java.util.Set;

/**
 * The persistent entity {@link CriteriaUpdate}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentEntityCriteriaUpdate<T> extends CriteriaUpdate<T>, PersistentEntityCommonAbstractCriteria {

    PersistentEntityRoot<T> from(PersistentEntity persistentEntity);

    @Override

    PersistentEntityRoot<T> from(Class<T> entityClass);

    @Override
    PersistentEntityRoot<T> from(EntityType<T> entity);

    @Override
    PersistentEntityRoot<T> getRoot();

    @Override
     <Y, X extends Y> PersistentEntityCriteriaUpdate<T> set(SingularAttribute<? super T, Y> attribute, @Nullable X value);

    @Override
     <Y> PersistentEntityCriteriaUpdate<T> set(SingularAttribute<? super T, Y> attribute,  Expression<? extends Y> value);

    @Override
     <Y, X extends Y> PersistentEntityCriteriaUpdate<T> set(Path<Y> attribute, @Nullable X value);

    @Override
     <Y> PersistentEntityCriteriaUpdate<T> set(Path<Y> attribute,  Expression<? extends Y> value);

    @Override

    PersistentEntityCriteriaUpdate<T> set(String attributeName, @Nullable Object value);

    @Override

    PersistentEntityCriteriaUpdate<T> where(Expression<Boolean> restriction);

    @Override

    PersistentEntityCriteriaUpdate<T> where(Predicate... restrictions);

    @Override
    Set<ParameterExpression<?>> getParameters();

    /**
     * The returning result of the query.
     *
     * @param selection The selection to return
     * @return The update criteria.
     * @since 4.2.0
     */
    @Experimental

    PersistentEntityCriteriaUpdate<T> returning(Selection<? extends T> selection);

    /**
     * The returning result of the query.
     *
     * @param selections The multi selection to return
     * @return The update criteria.
     * @since 4.2.0
     */
    @Experimental

    default PersistentEntityCriteriaUpdate<T> returningMulti(Selection<?>... selections) {
        return returningMulti(List.of(selections));
    }

    /**
     * The returning result of the query.
     *
     * @param selectionList The multi selection to return
     * @return The update criteria.
     * @since 4.2.0
     */
    @Experimental

    PersistentEntityCriteriaUpdate<T> returningMulti(List<Selection<?>> selectionList);

}

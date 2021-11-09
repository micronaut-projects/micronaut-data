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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.PersistentEntity;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;

import java.util.Set;

/**
 * The persistent entity {@link CriteriaUpdate}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentEntityCriteriaUpdate<T> extends CriteriaUpdate<T> {

    @NonNull
    PersistentEntityRoot<T> from(@NonNull PersistentEntity persistentEntity);

    @Override
    @NonNull
    PersistentEntityRoot<T> from(@NonNull Class<T> entityClass);

    @Override
    @NonNull
    PersistentEntityRoot<T> from(@NonNull EntityType<T> entity);

    @Override
    @NonNull
    PersistentEntityRoot<T> getRoot();

    @Override
    @NonNull
    <Y, X extends Y> PersistentEntityCriteriaUpdate<T> set(@NonNull SingularAttribute<? super T, Y> attribute, @Nullable X value);

    @Override
    @NonNull
    <Y> PersistentEntityCriteriaUpdate<T> set(@NonNull SingularAttribute<? super T, Y> attribute, @NonNull Expression<? extends Y> value);

    @Override
    @NonNull
    <Y, X extends Y> PersistentEntityCriteriaUpdate<T> set(@NonNull Path<Y> attribute, @Nullable X value);

    @Override
    @NonNull
    <Y> PersistentEntityCriteriaUpdate<T> set(@NonNull Path<Y> attribute, @NonNull Expression<? extends Y> value);

    @Override
    @NonNull
    PersistentEntityCriteriaUpdate<T> set(@NonNull String attributeName, @Nullable Object value);

    @Override
    @NonNull
    PersistentEntityCriteriaUpdate<T> where(@NonNull Expression<Boolean> restriction);

    @Override
    @NonNull
    PersistentEntityCriteriaUpdate<T> where(@NonNull Predicate... restrictions);

    @NonNull
    Set<ParameterExpression<?>> getParameters();
}

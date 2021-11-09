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
import io.micronaut.data.model.PersistentEntity;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.EntityType;

/**
 * The persistent entity {@link CriteriaDelete}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentEntityCriteriaDelete<T> extends CriteriaDelete<T> {

    PersistentEntityRoot<T> from(PersistentEntity persistentEntity);

    @Override
    PersistentEntityRoot<T> from(Class<T> entityClass);

    @Override
    PersistentEntityRoot<T> from(EntityType<T> entity);

    @Override
    PersistentEntityRoot<T> getRoot();

    @Override
    PersistentEntityCriteriaDelete<T> where(Expression<Boolean> restriction);

    @Override
    PersistentEntityCriteriaDelete<T> where(Predicate... restrictions);

}

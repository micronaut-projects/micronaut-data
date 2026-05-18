/*
 * Copyright 2017-2025 original authors
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
import jakarta.persistence.criteria.ParameterExpression;

import java.util.Set;

/**
 * The persistent entity insert.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 5.0
 */
@Experimental
public interface PersistentEntityCriteriaInsert<T> extends PersistentEntityCriteriaQueryBuilder {

    /**
     * The root entity.
     * @return The root entity
     */
    PersistentEntityRoot<T> getRoot();

    /**
     * The persistent entity.
     * @return The persistent entity
     */
    PersistentEntity getPersistentEntity();

    /**
     * Set returning the entity as a result.
     */
    @Internal
    void setReturning();

    /**
     * @return The parameters
     */
    Set<ParameterExpression<?>> getParameters();

}

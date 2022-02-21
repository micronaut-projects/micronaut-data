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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.impl.IdExpression;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

/**
 * The persistent entity {@link Root}.
 *
 * @param <T> The root type
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface PersistentEntityRoot<T> extends Root<T>, PersistentEntityFrom<T, T> {

    /**
     * Returns the entity ID expression.
     *
     * @param <Y> The id type
     * @return The ID expression
     */
    @NonNull
    default <Y> Expression<Y> id() {
        PersistentEntity persistentEntity = getPersistentEntity();
        if (persistentEntity.hasIdentity()) {
            return get(persistentEntity.getIdentity().getName());
        } else if (persistentEntity.hasCompositeIdentity()) {
            return new IdExpression<>(this);
        }
        throw new IllegalStateException("No identity is present");
    }

    /**
     * Returns the entity version expression.
     *
     * @param <Y> The version type
     * @return The version expression
     */
    @NonNull
    default <Y> PersistentPropertyPath<Y> version() {
        PersistentEntity persistentEntity = getPersistentEntity();
        if (persistentEntity.getVersion() == null) {
            throw new IllegalStateException("No version is present");
        }
        return get(persistentEntity.getVersion().getName());
    }

}

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
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
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
            return get(persistentEntity.getIdentity());
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
        PersistentProperty version = persistentEntity.getVersion();
        if (version == null) {
            throw new IllegalStateException("No version is present");
        }
        return get(version);
    }

    /**
     * Returns the property expression.
     *
     * @param persistentProperty The persistent property
     * @param <Y> The persistent property
     * @return The property expression
     * @since 4.8.0
     */
    @NonNull
    default <Y> PersistentPropertyPath<Y> get(@NonNull PersistentProperty persistentProperty) {
        return get(persistentProperty.getName());
    }

    @Override
    default void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

}

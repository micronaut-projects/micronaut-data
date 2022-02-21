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
package io.micronaut.data.model.jpa.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;

/**
 * The ID expression implementation.
 *
 * @param <E> The entity type
 * @param <T> The ID type
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public final class IdExpression<E, T> implements IExpression<T>, SelectionVisitable {

    private final PersistentEntityRoot<E> root;

    public IdExpression(PersistentEntityRoot<E> root) {
        this.root = root;
    }

    public PersistentEntityRoot<E> getRoot() {
        return root;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    @Override
    public boolean isBoolean() {
        PersistentEntity persistentEntity = root.getPersistentEntity();
        if (persistentEntity.hasCompositeIdentity()) {
            return false;
        }
        return root.get(persistentEntity.getIdentity().getName()).isBoolean();
    }

    @Override
    public boolean isNumeric() {
        PersistentEntity persistentEntity = root.getPersistentEntity();
        if (persistentEntity.hasCompositeIdentity()) {
            return false;
        }
        return root.get(persistentEntity.getIdentity().getName()).isNumeric();
    }

    @Override
    public Class<? extends T> getJavaType() {
        PersistentEntity persistentEntity = root.getPersistentEntity();
        if (persistentEntity.hasCompositeIdentity()) {
            throw new IllegalStateException("IdClass is unknown!");
        }
        return (Class<? extends T>) root.get(persistentEntity.getIdentity().getName()).getJavaType();
    }

    @Override
    public String toString() {
        return "IdExpression{" +
                "root=" + root +
                '}';
    }

}

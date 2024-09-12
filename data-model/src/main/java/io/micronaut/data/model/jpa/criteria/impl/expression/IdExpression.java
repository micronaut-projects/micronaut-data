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
package io.micronaut.data.model.jpa.criteria.impl.expression;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;

/**
 * The ID expression implementation.
 *
 * @param <E> The entity type
 * @param <T> The ID type
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public final class IdExpression<E, T> extends AbstractExpression<T> {

    private final PersistentEntityRoot<E> root;

    public IdExpression(PersistentEntityRoot<E> root) {
        super(getExpressionType(root));
        this.root = root;
    }

    private static <T> ExpressionType<T> getExpressionType(PersistentEntityRoot<?> root) {
        PersistentEntity persistentEntity = root.getPersistentEntity();
        if (persistentEntity.hasCompositeIdentity()) {
            return (ExpressionType<T>) ExpressionType.OBJECT;
        }
        return (ExpressionType<T>) root.get(persistentEntity.getIdentity().getName()).getExpressionType();
    }

    @Override
    public Class<? extends T> getJavaType() {
        PersistentEntity persistentEntity = root.getPersistentEntity();
        if (persistentEntity.hasCompositeIdentity()) {
            throw new IllegalStateException("IdClass is unknown!");
        }
        return (Class<? extends T>) root.get(persistentEntity.getIdentity().getName()).getJavaType();
    }

    public PersistentEntityRoot<E> getRoot() {
        return root;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "IdExpression{" +
            "root=" + root +
            '}';
    }

}

/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.graph.model.criteria;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.expression.AbstractExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.ClassExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.util.Joiner;

/**
 * A schemaless property of the root node, which the entity does not declare and the build cannot check.
 *
 * @param <E> The entity type of the root
 * @param <T> The type of the value
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DynamicPropertyExpression<E, T> extends AbstractExpression<T> {

    private final PersistentEntityRoot<E> root;
    private final String name;

    /**
     * @param root The root of the query
     * @param name The property name
     * @param type The type of the value
     */
    public DynamicPropertyExpression(PersistentEntityRoot<E> root, String name, Class<T> type) {
        super(new ClassExpressionType<>(type));
        this.root = root;
        this.name = GraphEntities.requireName(name);
    }

    /**
     * @return The root of the query
     */
    public PersistentEntityRoot<E> getRoot() {
        return root;
    }

    /**
     * @return The property name
     */
    public String getName() {
        return name;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        if (expressionVisitor instanceof GraphExpressionVisitor graphExpressionVisitor) {
            graphExpressionVisitor.visit(this);
        } else if (!(expressionVisitor instanceof Joiner)) {
            // The join collector has nothing to collect here: the property is read off the root node
            throw new UnsupportedOperationException("Schemaless property criteria can only be used with graph repositories");
        }
    }

    @Override
    public String toString() {
        return "DynamicPropertyExpression{name=" + name + '}';
    }
}

/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.SubqueryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import jakarta.persistence.criteria.Predicate;

/**
 * The expression visitor.
 *
 * @author Denis Stepanov
 * @since 4.9
 */
@Internal
public interface ExpressionVisitor {

    /**
     * Visit {@link Predicate}.
     *
     * @param predicate The predicate
     */
    default void visit(Predicate predicate) {
        throw new IllegalStateException("Predicate is not allowed as a selection!");
    }

    /**
     * Visit {@link PersistentPropertyPath}.
     *
     * @param persistentPropertyPath The persistentPropertyPath
     */
    void visit(PersistentPropertyPath<?> persistentPropertyPath);

    /**
     * Visit {@link PersistentEntityRoot}.
     *
     * @param entityRoot The entityRoot
     */
    void visit(PersistentEntityRoot<?> entityRoot);

    /**
     * Visit {@link PersistentEntitySubquery}.
     *
     * @param subquery The subquery
     */
    void visit(PersistentEntitySubquery<?> subquery);

    /**
     * Visit {@link LiteralExpression}.
     *
     * @param literalExpression The literalExpression
     */
    void visit(LiteralExpression<?> literalExpression);

    /**
     * Visit {@link UnaryExpression}.
     *
     * @param unaryExpression The unary expression
     */
    void visit(UnaryExpression<?> unaryExpression);

    /**
     * Visit {@link BinaryExpression}.
     *
     * @param binaryExpression The aggregateExpression
     */
    void visit(BinaryExpression<?> binaryExpression);

    /**
     * Visit {@link IdExpression}.
     *
     * @param idExpression The idExpression
     */
    void visit(IdExpression<?, ?> idExpression);

    /**
     * Visit {@link FunctionExpression}.
     *
     * @param functionExpression The function expression
     */
    void visit(FunctionExpression<?> functionExpression);

    /**
     * Visit {@link IParameterExpression}.
     *
     * @param parameterExpression The parameter expression
     */
    void visit(IParameterExpression<?> parameterExpression);

    /**
     * Visit {@link SubqueryExpression}.
     *
     * @param subqueryExpression The subquery expression
     */
    void visit(SubqueryExpression<?> subqueryExpression);
}

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
package io.micronaut.data.model.jpa.criteria.impl.expression;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;

/**
 * The current temporal expression.
 *
 * @param <E> The current temporal expression type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class CurrentTemporalExpression<E> extends AbstractExpression<E> {

    private final Type type;

    public CurrentTemporalExpression(Type type, Class<E> expressionType) {
        super(new ClassExpressionType<>(expressionType));
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "CurrentTemporalExpression{" +
            "type=" + type +
            '}';
    }

    /**
     * The current temporal expression type.
     */
    public enum Type {
        /**
         * Current date.
         */
        DATE,
        /**
         * Current time.
         */
        TIME,
        /**
         * Current timestamp.
         */
        TIMESTAMP
    }
}

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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;

/**
 * The subquery expression implementation.
 *
 * @param <T> The subquery type
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public final class SubqueryExpression<T> extends AbstractExpression<T> {

    private final Type type;
    private final PersistentEntitySubquery<T> subquery;

    public SubqueryExpression(@NonNull Type type, @NonNull PersistentEntitySubquery<T> subquery) {
        super(subquery.getExpressionType());
        this.type = type;
        this.subquery = subquery;
    }

    public PersistentEntitySubquery<T> getSubquery() {
        return subquery;
    }

    public Type getType() {
        return type;
    }

    @Override
    public Class<? extends T> getJavaType() {
        return super.getJavaType();
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        expressionVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "SubqueryExpression{" +
            "type=" + type +
            ", subquery=" + subquery +
            '}';
    }

    /**
     * The type of the expression.
     */
    public enum Type {
        ALL, SOME, ANY
    }

}

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
package io.micronaut.data.model.jpa.criteria.impl.predicate;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitable;
import io.micronaut.data.model.jpa.criteria.impl.PredicateVisitor;
import jakarta.persistence.criteria.Expression;

/**
 * The expression binary operation predicate implementation.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public final class ExpressionBinaryPredicate extends AbstractPredicate implements PredicateVisitable {

    private final Expression<?> left;
    private final Expression<?> right;
    private final PredicateBinaryOp op;

    public ExpressionBinaryPredicate(Expression<?> left,
                                     Expression<?> right,
                                     PredicateBinaryOp op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }

    public PredicateBinaryOp getOp() {
        return op;
    }

    public Expression<?> getLeft() {
        return left;
    }

    public Expression<?> getRight() {
        return right;
    }

    @Override
    public void accept(PredicateVisitor predicateVisitor) {
        predicateVisitor.visit(this);
    }

    @Override
    public String toString() {
        return "ExpressionBinaryPredicate{" +
                "left=" + left +
                ", right=" + right +
                ", op=" + op +
                '}';
    }
}

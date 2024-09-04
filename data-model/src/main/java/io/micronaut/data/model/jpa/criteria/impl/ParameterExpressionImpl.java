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
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.AbstractExpression;
import io.micronaut.data.model.query.BindingParameter;
import jakarta.persistence.criteria.ParameterExpression;

/**
 * The abstract implementation of {@link ParameterExpression}.
 *
 * @param <T> The parameter type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public abstract class ParameterExpressionImpl<T> extends AbstractExpression<T> implements ParameterExpression<T>, IExpression<T>, BindingParameter {

    private final String name;

    public ParameterExpressionImpl(ExpressionType<T> type, String name) {
        super(type);
        this.name = name;
    }

    @Override
    public void visitExpression(ExpressionVisitor expressionVisitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<T> getParameterType() {
        return getExpressionType().getJavaType();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Integer getPosition() {
        return null;
    }

    @Override
    public String toString() {
        return "ParameterExpressionImpl{" +
            "type=" + getExpressionType() +
            ", name='" + name + '\'' +
            '}';
    }
}

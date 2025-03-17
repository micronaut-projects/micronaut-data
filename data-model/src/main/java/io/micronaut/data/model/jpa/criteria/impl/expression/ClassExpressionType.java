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
package io.micronaut.data.model.jpa.criteria.impl.expression;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;

/**
 * The expression type represented by a class.
 *
 * @param <E> The type
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public final class ClassExpressionType<E> implements ExpressionType<E> {

    private final Class<E> expressionType;

    public ClassExpressionType(Class<E> expressionType) {
        this.expressionType = expressionType;
    }

    @Override
    public String getName() {
        return expressionType.getName();
    }

    @Override
    public boolean isBoolean() {
        if (expressionType != null) {
            return CriteriaUtils.isBoolean(expressionType);
        }
        return false;
    }

    @Override
    public boolean isNumeric() {
        if (expressionType != null) {
            return CriteriaUtils.isNumeric(expressionType);
        }
        return true;
    }

    @Override
    public boolean isComparable() {
        if (expressionType != null) {
            return CriteriaUtils.isComparable(expressionType);
        }
        return false;
    }

    @Override
    public boolean isTextual() {
        if (expressionType != null) {
            return CriteriaUtils.isTextual(expressionType);
        }
        return false;
    }

    @Override
    public Class<E> getJavaType() {
        return expressionType;
    }

}

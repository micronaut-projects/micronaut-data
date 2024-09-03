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
package io.micronaut.data.processor.model.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;

/**
 * A source expression.
 *
 * @param <T> The expression type
 */
@Internal
public interface SourceExpression<T> extends IExpression<T> {

    /**
     * @return The source expression type
     */
    @NonNull
    ClassElement getSourceExpressionType();

    @Override
    default boolean isBoolean() {
        return TypeUtils.isBoolean(getSourceExpressionType());
    }

    @Override
    default boolean isNumeric() {
        return TypeUtils.isNumber(getSourceExpressionType());
    }

    @Override
    default boolean isComparable() {
        return TypeUtils.isComparable(getSourceExpressionType());
    }

    @Override
    default boolean isTextual() {
        return TypeUtils.isTextual(getSourceExpressionType());
    }
}

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
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The expression type represented by {@link ClassElement}.
 *
 * @param <T> Teh type
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public final class ClassElementExpressionType<T> implements ExpressionType<T> {

    private final ClassElement type;

    public ClassElementExpressionType(@NonNull ClassElement type) {
        this.type = type;
    }

    @Override
    public String getName() {
        return type.getName();
    }

    @Override
    public boolean isBoolean() {
        return TypeUtils.isBoolean(type);
    }

    @Override
    public boolean isNumeric() {
        return TypeUtils.isNumber(type);
    }

    @Override
    public boolean isComparable() {
        return TypeUtils.isComparable(type);
    }

    @Override
    public boolean isTextual() {
        return TypeUtils.isTextual(type);
    }

    @Override
    public Class<T> getJavaType() {
        throw notSupportedOperation();
    }
}

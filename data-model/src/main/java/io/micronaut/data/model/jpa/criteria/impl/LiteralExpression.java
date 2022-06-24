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
import io.micronaut.data.model.jpa.criteria.IExpression;

/**
 * The literal expression implementation.
 *
 * @param <T> The literal type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class LiteralExpression<T> implements IExpression<T>, SelectionVisitable {

    private final T value;

    public LiteralExpression(T object) {
        this.value = object;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    public T getValue() {
        return value;
    }

    @Override
    public boolean isBoolean() {
        return value instanceof Boolean;
    }

    @Override
    public boolean isNumeric() {
        return value instanceof Number;
    }

    @Override
    public boolean isComparable() {
        return value instanceof Comparable;
    }

    @Override
    public Class<? extends T> getJavaType() {
        return (Class<? extends T>) value.getClass();
    }

    @Override
    public String toString() {
        return "LiteralExpression{" +
                "value=" + value +
                '}';
    }

}

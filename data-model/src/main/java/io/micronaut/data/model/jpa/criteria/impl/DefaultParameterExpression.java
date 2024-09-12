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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.ClassExpressionType;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

/**
 * The default parameter expression implementation.
 *
 * @param <T> The parameter type
 * @author Denis Stepanov
 * @since 4.9.0
 */
@Internal
final class DefaultParameterExpression<T> extends IParameterExpression<T> {

    private final @NonNull Class<T> paramClass;
    private final @Nullable Object value;

    public DefaultParameterExpression(@NonNull Class<T> paramClass, @Nullable String name, @Nullable Object value) {
        super(new ClassExpressionType<>(paramClass), name);
        this.paramClass = paramClass;
        this.value = value;
    }

    @Override
    public QueryParameterBinding bind(BindingContext bindingContext) {
        String name = bindingContext.getName() == null ? String.valueOf(bindingContext.getIndex()) : bindingContext.getName();
        PersistentPropertyPath outgoingQueryParameterProperty = bindingContext.getOutgoingQueryParameterProperty();
        if (outgoingQueryParameterProperty == null) {
            return new SimpleParameterBinding(name, DataType.forType(paramClass), bindingContext.isExpandable(), value);
        }
        return new PropertyPathParameterBinding(name, outgoingQueryParameterProperty, bindingContext.isExpandable(), value);
    }
}

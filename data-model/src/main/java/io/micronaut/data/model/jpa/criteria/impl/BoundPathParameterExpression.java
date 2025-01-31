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
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

/**
 * The parameter value with a bound path.
 *
 * @param <T> The parameter type
 * @author Denis Stepanov
 * @since 4.12.0
 */
@Internal
public final class BoundPathParameterExpression<T> extends IParameterExpression<T> {

    private final IParameterExpression<T> originalParameterExpression;
    private final PersistentPropertyPath propertyPath;

    public BoundPathParameterExpression(IParameterExpression<T> originalParameterExpression, PersistentPropertyPath propertyPath) {
        super(originalParameterExpression.getExpressionType(), originalParameterExpression.getName());
        this.originalParameterExpression = originalParameterExpression;
        this.propertyPath = propertyPath;
    }

    @Override
    public QueryParameterBinding bind(BindingContext bindingContext) {
        return originalParameterExpression.bind(bindingContext.parameterBindingPath(propertyPath));
    }
}

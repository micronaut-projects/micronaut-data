/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.graph.model.query.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

/**
 * The parameter of a {@code LIKE} pattern: either the regular expression of a pattern known when the query is
 * built, or the pattern itself with the converter that translates it at bind time.
 */
@Internal
final class LikeParameterBinding implements QueryParameterBinding {
    private final QueryParameterBinding delegate;
    private final @Nullable String regex;
    private final @Nullable String converterClassName;

    LikeParameterBinding(QueryParameterBinding delegate, @Nullable String regex, @Nullable String converterClassName) {
        this.delegate = delegate;
        this.regex = regex;
        this.converterClassName = converterClassName;
    }

    @Override
    public @Nullable Object getValue() {
        return regex != null ? regex : delegate.getValue();
    }

    @Override
    public @Nullable String getConverterClassName() {
        return converterClassName != null ? converterClassName : delegate.getConverterClassName();
    }

    @Override
    public @Nullable String getName() {
        return delegate.getName();
    }

    @Override
    public String getKey() {
        return delegate.getKey();
    }

    @Override
    public DataType getDataType() {
        return delegate.getDataType();
    }

    @Override
    public JsonDataType getJsonDataType() {
        return delegate.getJsonDataType();
    }

    @Override
    public int getParameterIndex() {
        return delegate.getParameterIndex();
    }

    @Override
    public String @Nullable [] getParameterBindingPath() {
        return delegate.getParameterBindingPath();
    }

    @Override
    public String @Nullable [] getPropertyPath() {
        return delegate.getPropertyPath();
    }

    @Override
    public boolean isAutoPopulated() {
        return delegate.isAutoPopulated();
    }

    @Override
    public boolean isRequiresPreviousPopulatedValue() {
        return delegate.isRequiresPreviousPopulatedValue();
    }

    @Override
    public boolean isExpandable() {
        return delegate.isExpandable();
    }

    @Override
    public boolean isExpression() {
        return delegate.isExpression();
    }

    @Override
    public @Nullable String getRole() {
        return delegate.getRole();
    }

    @Override
    public @Nullable String getTableAlias() {
        return delegate.getTableAlias();
    }
}

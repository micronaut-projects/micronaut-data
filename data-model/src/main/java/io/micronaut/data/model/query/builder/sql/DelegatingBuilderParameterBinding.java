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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import org.jspecify.annotations.Nullable;

/**
 * A {@link QueryParameterBinding} that delegates every call, for subclasses that override one.
 *
 * @since 5.4.0
 */
@Internal
abstract class DelegatingBuilderParameterBinding implements QueryParameterBinding {

    private final QueryParameterBinding delegate;

    DelegatingBuilderParameterBinding(QueryParameterBinding delegate) {
        this.delegate = delegate;
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
    public @Nullable String getConverterClassName() {
        return delegate.getConverterClassName();
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
    public @Nullable Object getValue() {
        return delegate.getValue();
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

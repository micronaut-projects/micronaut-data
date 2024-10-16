/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;

/**
 * Delegating {@link QueryParameterBinding}. Intended for overriding some of the {@link QueryParameterBinding}'s properties.
 *
 * @author Denis Stepanov
 * @since 3.8.0
 */
@Internal
public abstract class DelegatingQueryParameterBinding implements QueryParameterBinding {

    private final QueryParameterBinding delegate;

    protected DelegatingQueryParameterBinding(QueryParameterBinding delegate) {
        this.delegate = delegate;
    }

    @Override
    @Nullable
    public String getName() {
        return delegate.getName();
    }

    @Override
    @NonNull
    public String getRequiredName() {
        return delegate.getRequiredName();
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
    public Class<?> getParameterConverterClass() {
        return delegate.getParameterConverterClass();
    }

    @Override
    public int getParameterIndex() {
        return delegate.getParameterIndex();
    }

    @Override
    public String[] getParameterBindingPath() {
        return delegate.getParameterBindingPath();
    }

    @Override
    public String[] getPropertyPath() {
        return delegate.getPropertyPath();
    }

    @Override
    public String[] getRequiredPropertyPath() {
        return delegate.getRequiredPropertyPath();
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
    public QueryParameterBinding getPreviousPopulatedValueParameter() {
        return delegate.getPreviousPopulatedValueParameter();
    }

    @Override
    public boolean isExpandable() {
        return delegate.isExpandable();
    }

    @Override
    public Object getValue() {
        return delegate.getValue();
    }

    @Override
    public String getRole() {
        return delegate.getRole();
    }

    @Override
    public String getTableAlias() {
        return delegate.getTableAlias();
    }
}

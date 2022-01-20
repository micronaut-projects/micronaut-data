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
package io.micronaut.data.runtime.query.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.QueryParameterBinding;

import java.util.Arrays;
import java.util.List;

/**
 * The stored query parameter.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public final class StoredQueryParameter implements QueryParameterBinding {

    private final String name;
    private final DataType dataType;
    private final int parameterIndex;
    private final String[] parameterBindingPath;
    private final String[] propertyPath;
    private final boolean autoPopulated;
    private final boolean requiresPreviousPopulatedValue;
    private final Class<?> parameterConverterClass;
    private final boolean expandable;
    private final List<? extends QueryParameterBinding> all;

    private boolean previousInitialized;
    private QueryParameterBinding previousPopulatedValueParameter;

    StoredQueryParameter(String name,
                         DataType dataType,
                         int parameterIndex,
                         String[] parameterBindingPath,
                         String[] propertyPath,
                         boolean autoPopulated,
                         boolean requiresPreviousPopulatedValue,
                         Class<?> parameterConverterClass,
                         boolean expandable,
                         List<? extends QueryParameterBinding> all) {
        this.name = name;
        this.dataType = dataType;
        this.parameterIndex = parameterIndex;
        this.parameterBindingPath = parameterBindingPath;
        this.propertyPath = propertyPath;
        this.autoPopulated = autoPopulated;
        this.requiresPreviousPopulatedValue = requiresPreviousPopulatedValue;
        this.parameterConverterClass = parameterConverterClass;
        this.expandable = expandable;
        this.all = all;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DataType getDataType() {
        return dataType;
    }

    @Override
    public Class<?> getParameterConverterClass() {
        return parameterConverterClass;
    }

    @Override
    public int getParameterIndex() {
        return parameterIndex;
    }

    @Override
    public String[] getParameterBindingPath() {
        return parameterBindingPath;
    }

    @Override
    public String[] getPropertyPath() {
        return propertyPath;
    }

    @Override
    public boolean isAutoPopulated() {
        return autoPopulated;
    }

    @Override
    public boolean isRequiresPreviousPopulatedValue() {
        return requiresPreviousPopulatedValue;
    }

    @Override
    public QueryParameterBinding getPreviousPopulatedValueParameter() {
        if (!previousInitialized) {
            for (QueryParameterBinding it : all) {
                if (it != this && it.getParameterIndex() != -1 && Arrays.equals(propertyPath, it.getPropertyPath())) {
                    previousPopulatedValueParameter = it;
                    break;
                }
            }
            previousInitialized = true;
        }
        return previousPopulatedValueParameter;
    }

    @Override
    public boolean isExpandable() {
        return expandable;
    }

    @Override
    public String toString() {
        return "StoredQueryParameter{" +
                "name='" + name + '\'' +
                ", dataType=" + dataType +
                ", parameterIndex=" + parameterIndex +
                ", parameterBindingPath=" + Arrays.toString(parameterBindingPath) +
                ", propertyPath=" + Arrays.toString(propertyPath) +
                ", autoPopulated=" + autoPopulated +
                ", requiresPreviousPopulatedValue=" + requiresPreviousPopulatedValue +
                ", previousPopulatedValueParameter=" + previousPopulatedValueParameter +
                ", expandable=" + expandable +
                '}';
    }
}

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
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The stored query parameter.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public final class StoredQueryParameter implements QueryParameterBinding {

    @Nullable
    private final String name;
    @Nullable
    private final DataType dataType;
    @Nullable
    private final JsonDataType jsonDataType;
    private final int parameterIndex;
    private final String @Nullable [] parameterBindingPath;
    private final String @Nullable [] propertyPath;
    private final boolean autoPopulated;
    private final boolean requiresPreviousPopulatedValue;
    @Nullable
    private final Class<?> parameterConverterClass;
    private final boolean expandable;
    private final boolean nativeBoolean;
    private final List<QueryParameterBinding> all;
    private final boolean expression;
    @Nullable
    private final Object value;
    @Nullable
    private final String role;
    @Nullable
    private final String tableAlias;

    private boolean previousInitialized;
    @Nullable
    private QueryParameterBinding previousPopulatedValueParameter;

    @SuppressWarnings("checkstyle:ParameterNumber")
    StoredQueryParameter(@Nullable String name,
                         @Nullable DataType dataType,
                         @Nullable JsonDataType jsonDataType,
                         int parameterIndex,
                         String @Nullable [] parameterBindingPath,
                         String @Nullable [] propertyPath,
                         boolean autoPopulated,
                         boolean requiresPreviousPopulatedValue,
                         @Nullable Class<?> parameterConverterClass,
                         boolean expandable,
                         boolean nativeBoolean,
                         final boolean expression,
                         @Nullable Object value,
                         @Nullable String role,
                         @Nullable String tableAlias,
                         List<QueryParameterBinding> all) {
        this.name = name;
        this.dataType = dataType;
        this.jsonDataType = jsonDataType;
        this.parameterIndex = parameterIndex;
        this.parameterBindingPath = parameterBindingPath;
        this.propertyPath = propertyPath;
        this.autoPopulated = autoPopulated;
        this.requiresPreviousPopulatedValue = requiresPreviousPopulatedValue;
        this.parameterConverterClass = parameterConverterClass;
        this.expandable = expandable;
        this.nativeBoolean = nativeBoolean;
        this.expression = expression;
        this.value = value;
        this.role = role;
        this.tableAlias = tableAlias;
        this.all = all;
    }

    @Override
    @Nullable
    public String getName() {
        return name;
    }

    @Override
    public DataType getDataType() {
        return Objects.requireNonNullElse(dataType, DataType.OBJECT);
    }

    @Override
    public JsonDataType getJsonDataType() {
        return Objects.requireNonNullElse(jsonDataType, JsonDataType.DEFAULT);
    }

    @Nullable
    @Override
    public Class<?> getParameterConverterClass() {
        return parameterConverterClass;
    }

    @Override
    public int getParameterIndex() {
        return parameterIndex;
    }

    @Override
    public String @Nullable [] getParameterBindingPath() {
        return parameterBindingPath;
    }

    @Override
    public String @Nullable [] getPropertyPath() {
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
    @Nullable
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
    public boolean isNativeBoolean() {
        return nativeBoolean;
    }

    @Override
    public boolean isExpression() {
        return expression;
    }

    @Override
    @Nullable
    public Object getValue() {
        return value;
    }

    @Override
    @Nullable
    public String getRole() {
        return role;
    }

    @Override
    @Nullable
    public String getTableAlias() {
        return tableAlias;
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
                ", expression=" + expression +
                ", value=" + value +
                ", role=" + role +
                ", tableAlias=" + tableAlias +
                '}';
    }
}

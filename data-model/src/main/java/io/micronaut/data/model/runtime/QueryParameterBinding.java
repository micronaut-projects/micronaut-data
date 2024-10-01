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
package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;

/**
 * The query parameter binding.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface QueryParameterBinding {

    /**
     * @return The name of the parameter
     */
    @Nullable
    default String getName() {
        return null;
    }

    /**
     * @return The required name of the parameter or throws exception
     */
    @NonNull
    default String getRequiredName() {
        String name = getName();
        if (name == null) {
            throw new IllegalStateException("Parameter name cannot be null for a query parameter: " + this);
        }
        return name;
    }

    /**
     * @return The data type
     */
    @Nullable
    default DataType getDataType() {
        return null;
    }

    /**
     * @return The JSON representation type if data type is JSON, default {@link JsonDataType#DEFAULT}
     */
    @Nullable
    default JsonDataType getJsonDataType() {
        return null;
    }

    /**
     * @return The parameter converter class
     */
    @Nullable
    default Class<?> getParameterConverterClass() {
        return null;
    }

    /**
     * @return The parameter index
     */
    default int getParameterIndex() {
        return -1;
    }

    /**
     * @return The parameter binding property path.
     */
    @Nullable
    default String[] getParameterBindingPath() {
        return null;
    }

    /**
     * @return The property path.
     */
    @Nullable
    default String[] getPropertyPath() {
        return null;
    }

    /**
     * @return The required property path or throws and exception.
     */
    @NonNull
    default String[] getRequiredPropertyPath() {
        String[] propertyPath = getPropertyPath();
        if (propertyPath == null) {
            throw new IllegalStateException("Property path cannot be null for a query parameter: " + this);
        }
        return propertyPath;
    }

    /**
     * @return if property is auto-populated
     */
    default boolean isAutoPopulated() {
        return false;
    }

    /**
     * @return if property is auto-populated and binding requires previous value to be set.
     */
    default boolean isRequiresPreviousPopulatedValue() {
        return false;
    }

    /**
     * @return The previous value of the auto-populated property for cases when the property is mapped to the method parameter.
     */
    @Nullable
    default QueryParameterBinding getPreviousPopulatedValueParameter() {
        return null;
    }

    /**
     * @return Is expandable parameter
     */
    default boolean isExpandable() {
        return false;
    }

    /**
     * The constant runtime value for the parameter.
     *
     * @return the value or null
     */
    @Nullable
    default Object getValue() {
        return null;
    }

    /**
     * @return Is expression value
     * @since 4.5.0
     */
    default boolean isExpression() {
        return false;
    }

    /**
     * @return The parameter in role
     * @since 4.10
     */
    @Nullable
    default String getRole() {
        return null;
    }

    /**
     * @return The table alias
     * @since 4.10
     */
    @Nullable
    default String getTableAlias() {
        return null;
    }
}

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
    String getName();

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
    DataType getDataType();

    /**
     * @return The parameter converter class
     */
    @Nullable
    Class<?> getParameterConverterClass();

    /**
     * @return The parameter index
     */
    int getParameterIndex();

    /**
     * @return The parameter binding property path.
     */
    @Nullable
    String[] getParameterBindingPath();

    /**
     * @return The property path.
     */
    @Nullable
    String[] getPropertyPath();

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
    boolean isAutoPopulated();

    /**
     * @return if property is auto-populated and binding requires previous value to be set.
     */
    boolean isRequiresPreviousPopulatedValue();

    /**
     * @return The previous value of the auto-populated property for cases when the property is mapped to the method parameter.
     */
    @Nullable
    QueryParameterBinding getPreviousPopulatedValueParameter();
}

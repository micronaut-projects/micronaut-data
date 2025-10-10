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
package io.micronaut.data.model.query.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.JsonRepresentation;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;

/**
 * Query parameter binding, represents the data needed to bind a property to the query parameter.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
@Internal
public interface QueryParameterBinding {

    /**
     * @return The parameter name.
     */
    String getName();

    /**
     * @return The key represents the placeholder value in the query (usually it's ?).
     */
    String getKey();

    /**
     * @return The data type
     */
    DataType getDataType();

    /**
     * @return The json representation data type if getDataType is {@link DataType#JSON} and is annotated with {@link JsonRepresentation}
     * annotation
     */
    default JsonDataType getJsonDataType() {
        return JsonDataType.DEFAULT;
    }

    /**
     * @return The converter class name
     */
    @Nullable
    default String getConverterClassName() {
        return null;
    }

    /**
     * @return The parameter index
     */
    default int getParameterIndex() {
        return -1;
    }

    /**
     * @return The parameter binding path
     */
    @Nullable
    default String[] getParameterBindingPath() {
        return null;
    }

    /**
     * @return The property path
     */
    @Nullable
    default String[] getPropertyPath() {
        return null;
    }

    /**
     * @return Is auto populated
     */
    default boolean isAutoPopulated() {
        return false;
    }

    /**
     * @return Is requires previous populated value
     */
    default boolean isRequiresPreviousPopulatedValue() {
        return false;
    }

    /**
     * @return Is expandable parameter
     */
    default boolean isExpandable() {
        return false;
    }

    /**
     * The constant runtime value.
     *
     * @return The value or null
     * @since 3.5.0
     */
    @Nullable
    default Object getValue() {
        return null;
    }

    /**
     * The constant runtime value.
     *
     * @return The value or null
     * @since 4.5.0
     */
    @Nullable
    default boolean isExpression() {
        return false;
    }

    /**
     * The role of the parameter.
     *
     * @return The role name or null
     * @since 4.10
     */
    @Nullable
    default String getRole() {
        return null;
    }

    /**
     * The table alias.
     *
     * @return The table alias
     * @since 4.10
     */
    @Nullable
    default String getTableAlias() {
        return null;
    }
}

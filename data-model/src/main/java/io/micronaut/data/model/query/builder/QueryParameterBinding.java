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
import io.micronaut.data.model.DataType;

/**
 * Query parameter binding, represents the data needed to bind a property to the query parameter.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
@Internal
public interface QueryParameterBinding {

    /**
     * @return The key represents the placeholder value in the query (usually it's ?).
     */
    String getKey();

    DataType getDataType();

    @Nullable
    default String getConverterClassName() {
        return null;
    }

    default int getParameterIndex() {
        return -1;
    }

    @Nullable
    default String[] getParameterBindingPath() {
        return null;
    }

    @Nullable
    default String[] getPropertyPath() {
        return null;
    }

    default boolean isAutoPopulated() {
        return false;
    }

    default boolean isRequiresPreviousPopulatedValue() {
        return false;
    }

}

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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

/**
 * The simple {@link QueryParameterBinding}.
 *
 * @param getName      The name
 * @param dataType     The data type
 * @param isExpandable is expandable
 * @param value        The value
 * @author Denis Stepanov
 * @since 4.9.0
 */
@Internal
record SimpleParameterBinding(String getName,
                              DataType dataType,
                              boolean isExpandable,
                              @Nullable Object value) implements QueryParameterBinding {

    @Override
    public String getKey() {
        return getName;
    }

    @Override
    public DataType getDataType() {
        return dataType;
    }

    @Override
    public JsonDataType getJsonDataType() {
        return JsonDataType.DEFAULT;
    }

    @Override
    public String[] getPropertyPath() {
        return null;
    }

    @Override
    public Object getValue() {
        return value;
    }
}

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
package io.micronaut.data.model.query.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.query.BindingParameter;

/**
 * The {@link QueryParameterBinding} of the additional parameter.
 *
 * @param bindingContext The binding context
 * @param name           The property name
 * @author Denis Stepanov
 * @since 4.9.0
 */
@Internal
public record AdditionalParameterBinding(BindingParameter.BindingContext bindingContext,
                                         String name) implements QueryParameterBinding {

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getKey() {
        return bindingContext.getName();
    }

    @Override
    public DataType getDataType() {
        return DataType.OBJECT;
    }

    @Override
    public JsonDataType getJsonDataType() {
        return JsonDataType.DEFAULT;
    }
}

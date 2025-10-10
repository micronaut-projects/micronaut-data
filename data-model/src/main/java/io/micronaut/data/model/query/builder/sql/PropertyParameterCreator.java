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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.AdditionalBindingParameter;

interface PropertyParameterCreator {

    void pushParameter(@NonNull BindingParameter bindingParameter,
                       @NonNull BindingParameter.BindingContext bindingContext);

    default void pushAdditionalParameter(@NonNull String name) {
        pushParameter(
            new AdditionalBindingParameter(name),
            BindingParameter.BindingContext.create()
        );
    }

}

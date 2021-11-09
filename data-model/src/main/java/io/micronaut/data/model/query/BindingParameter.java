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
package io.micronaut.data.model.query;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

/**
 * The query binding parameter.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Experimental
public interface BindingParameter {

    /**
     * Bind the parameter.
     *
     * @param bindingContext The binding context
     * @return The query binding
     */
    @NonNull
    QueryParameterBinding bind(@NonNull BindingContext bindingContext);

    /**
     * The binding context.
     * <p>
     * Additional properties are used to map binding parameter path. Example method `findByAuthor(author)`
     * incoming method parameter property path would be `author` and query parameter property path `author.id`.
     */
    interface BindingContext {

        /**
         * Create new context.
         *
         * @return new bindign context
         */
        static BindingContext create() {
            return new BindingContextImpl();
        }

        /**
         * The index of the parameter in the query.
         *
         * @param index The index
         * @return this context
         */
        @NonNull
        BindingContext index(int index);

        /**
         * The name of the parameter in the query.
         *
         * @param name The name
         * @return this context
         */
        @NonNull
        BindingContext name(@Nullable String name);

        /**
         * The property that represents incoming method parameter property.
         *
         * @param propertyPath The property path
         * @return this context
         */
        @NonNull
        BindingContext incomingMethodParameterProperty(@Nullable PersistentPropertyPath propertyPath);

        /**
         * The property that represents outgoing query parameter property.
         *
         * @param propertyPath The property path
         * @return this context
         */
        @NonNull
        BindingContext outgoingQueryParameterProperty(@Nullable PersistentPropertyPath propertyPath);

        /**
         * The position of the parameter in the query.
         *
         * @return The index
         */
        int getIndex();

        /**
         * @return The name
         */
        @Nullable
        String getName();

        /**
         * @return The incomingMethodParameterProperty
         */
        @Nullable
        PersistentPropertyPath getIncomingMethodParameterProperty();

        /**
         * @return The outgoingQueryParameterProperty
         */
        @Nullable
        PersistentPropertyPath getOutgoingQueryParameterProperty();

    }

}

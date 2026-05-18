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

import io.micronaut.data.model.PersistentPropertyPath;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link BindingParameter.BindingContext}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
final class BindingContextImpl implements BindingParameter.BindingContext {

    private int index = -1;
    @Nullable
    private String name;
    @Nullable
    private PersistentPropertyPath incomingMethodParameterProperty;
    @Nullable
    private PersistentPropertyPath outgoingQueryParameterProperty;
    @Nullable
    private PersistentPropertyPath parameterBindingPath;
    private boolean expandable;

    @Override
    public BindingParameter.BindingContext index(int index) {
        this.index = index;
        return this;
    }

    @Override
    public BindingParameter.BindingContext name(@Nullable String name) {
        this.name = name;
        return this;
    }

    @Override
    public BindingParameter.BindingContext incomingMethodParameterProperty(@Nullable PersistentPropertyPath propertyPath) {
        this.incomingMethodParameterProperty = propertyPath;
        return this;
    }

    @Override
    public BindingParameter.BindingContext outgoingQueryParameterProperty(@Nullable PersistentPropertyPath propertyPath) {
        this.outgoingQueryParameterProperty = propertyPath;
        return this;
    }

    @Override
    public BindingParameter.BindingContext parameterBindingPath(@Nullable PersistentPropertyPath propertyPath) {
        this.parameterBindingPath = propertyPath;
        return this;
    }

    @Override
    public BindingParameter.BindingContext expandable() {
        this.expandable = true;
        return this;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    @Nullable
    public String getName() {
        return name;
    }

    @Override
    @Nullable
    public PersistentPropertyPath getIncomingMethodParameterProperty() {
        return incomingMethodParameterProperty;
    }

    @Override
    @Nullable
    public PersistentPropertyPath getOutgoingQueryParameterProperty() {
        return outgoingQueryParameterProperty;
    }

    @Override
    @Nullable
    public PersistentPropertyPath getParameterBindingPath() {
        return parameterBindingPath;
    }

    @Override
    public boolean isExpandable() {
        return expandable;
    }
}

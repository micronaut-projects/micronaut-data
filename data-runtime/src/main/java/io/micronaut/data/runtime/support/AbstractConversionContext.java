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
package io.micronaut.data.runtime.support;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.type.Argument;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Delegating {@link ConversionContext}.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public class AbstractConversionContext implements ConversionContext {

    private final ConversionContext delegate;

    public AbstractConversionContext(ConversionContext conversionContext) {
        this.delegate = conversionContext;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return delegate.getAnnotationMetadata();
    }

    @Override
    public Map<String, Argument<?>> getTypeVariables() {
        return delegate.getTypeVariables();
    }

    @Override
    public Argument[] getTypeParameters() {
        return delegate.getTypeParameters();
    }

    @Override
    public Optional<Argument<?>> getFirstTypeVariable() {
        return delegate.getFirstTypeVariable();
    }

    @Override
    public Optional<Argument<?>> getTypeVariable(String name) {
        return delegate.getTypeVariable(name);
    }

    @Override
    public void reject(Exception exception) {
        delegate.reject(exception);
    }

    @Override
    public void reject(Object value, Exception exception) {
        delegate.reject(value, exception);
    }

    @Override
    public Iterator<ConversionError> iterator() {
        return delegate.iterator();
    }

    @Override
    public Optional<ConversionError> getLastError() {
        return delegate.getLastError();
    }

    @Override
    public boolean hasErrors() {
        return delegate.hasErrors();
    }

}

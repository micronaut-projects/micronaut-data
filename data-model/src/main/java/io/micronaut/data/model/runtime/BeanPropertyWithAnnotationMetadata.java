/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;

import java.util.Optional;

/**
 * Custom {@link BeanProperty} with different annotation metadata.
 * @param <B> The bean type
 * @param <T> The bean property type
 * @since 4.2.0
 * @author Denis Stepanov
 */
@Internal
public final class BeanPropertyWithAnnotationMetadata<B, T> implements BeanProperty<B, T> {

    private final BeanProperty<B, T> delegate;
    private final Argument<T> argument;
    private final AnnotationMetadata annotationMetadata;

    public BeanPropertyWithAnnotationMetadata(BeanProperty<B, T> delegate, AnnotationMetadata annotationMetadata) {
        this.delegate = delegate;
        this.annotationMetadata = annotationMetadata;
        Argument<T> originalArgument = delegate.asArgument();
        this.argument = new DefaultArgument<>(
            originalArgument.getType(),
            originalArgument.getName(),
            annotationMetadata,
            originalArgument.getTypeVariables(),
            originalArgument.getTypeParameters()
        );
    }

    @Override
    public @NonNull BeanIntrospection<B> getDeclaringBean() {
        return delegate.getDeclaringBean();
    }

    @Override
    public @Nullable T get(@NonNull B bean) {
        return delegate.get(bean);
    }

    @Override
    public @NonNull <T2> Optional<T2> get(@NonNull B bean, @NonNull Class<T2> type) {
        return delegate.get(bean, type);
    }

    @Override
    public <T2> Optional<T2> get(@NonNull B bean, @NonNull Argument<T2> argument) {
        return delegate.get(bean, argument);
    }

    @Override
    public <T2> Optional<T2> get(@NonNull B bean, @NonNull ArgumentConversionContext<T2> conversionContext) {
        return delegate.get(bean, conversionContext);
    }

    @Override
    public <T2> @Nullable T2 get(@NonNull B bean, @NonNull Class<T2> type, @Nullable T2 defaultValue) {
        return delegate.get(bean, type, defaultValue);
    }

    @Override
    public boolean hasSetterOrConstructorArgument() {
        return delegate.hasSetterOrConstructorArgument();
    }

    @Override
    public B withValue(@NonNull B bean, @Nullable T value) {
        return delegate.withValue(bean, value);
    }

    @Override
    public void set(@NonNull B bean, @Nullable T value) {
        delegate.set(bean, value);
    }

    @Override
    public void convertAndSet(@NonNull B bean, @Nullable Object value) {
        delegate.convertAndSet(bean, value);
    }

    @Override
    public @NonNull Class<T> getType() {
        return delegate.getType();
    }

    @Override
    public @NonNull Argument<T> asArgument() {
        return argument;
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public boolean isWriteOnly() {
        return delegate.isWriteOnly();
    }

    @Override
    public boolean isReadWrite() {
        return delegate.isReadWrite();
    }

    @Override
    public Class<B> getDeclaringType() {
        return delegate.getDeclaringType();
    }

    @Override
    public @NonNull String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isDeclaredNullable() {
        return delegate.isDeclaredNullable();
    }

    @Override
    public boolean isNullable() {
        return delegate.isNullable();
    }

    @Override
    public boolean isNonNull() {
        return delegate.isNonNull();
    }

    @Override
    public boolean isDeclaredNonNull() {
        return delegate.isDeclaredNonNull();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }
}

/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.transaction.TransactionDefinition;

import java.util.Optional;

/**
 * Abstract implementation of {@link PreparedDataOperation}.
 *
 * @param <R> The result type
 * @author graemerocher
 * @since 2.2.0
 */
public abstract class AbstractPreparedDataOperation<R> implements PreparedDataOperation<R> {
    private final StoredDataOperation<R> storedDataOperation;
    private final MethodInvocationContext<?, R> context;

    protected AbstractPreparedDataOperation(MethodInvocationContext<?, R> context, StoredDataOperation<R> storedDataOperation) {
        this.storedDataOperation = storedDataOperation;
        this.context = context;
    }

    @NonNull
    @Override
    public final Optional<Object> getAttribute(CharSequence name) {
        return context.getAttribute(name);
    }

    @NonNull
    @Override
    public final <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        return context.getAttribute(name, type);
    }

    @NonNull
    @Override
    public final ConvertibleValues<Object> getAttributes() {
        return context.getAttributes();
    }

    @NonNull
    @Override
    public final Argument<R> getResultArgument() {
        return storedDataOperation.getResultArgument();
    }

    @NonNull
    @Override
    public final AnnotationMetadata getAnnotationMetadata() {
        return storedDataOperation.getAnnotationMetadata();
    }

    @NonNull
    @Override
    public final Optional<TransactionDefinition> getTransactionDefinition() {
        return storedDataOperation.getTransactionDefinition();
    }
}

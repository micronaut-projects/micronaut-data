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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.support.TransactionUtil;

import java.util.Optional;

/**
 * Used as a super class to resolve and potentially cache data about a method.
 *
 * @param <R> The return type.
 * @since 2.2.0
 * @author graemerocher
 */
public class DefaultStoredDataOperation<R> implements StoredDataOperation<R> {
    private final ExecutableMethod<?, ?> method;
    private TransactionDefinition transactionDefinition;

    public DefaultStoredDataOperation(ExecutableMethod<?, ?> method) {
        this.method = method;
    }

    @NonNull
    @Override
    public final Optional<TransactionDefinition> getTransactionDefinition() {
        if (transactionDefinition == null) {
            transactionDefinition = TransactionUtil.getTransactionDefinition(
                    method.getDeclaringType().getSimpleName() + "." + method.getMethodName(), method);
        }
        if (transactionDefinition == TransactionDefinition.DEFAULT) {
            return Optional.empty();
        }
        return Optional.of(transactionDefinition);
    }

    @NonNull
    @Override
    public final Argument<R> getResultArgument() {
        //noinspection unchecked
        return (Argument<R>) method.getReturnType().asArgument();
    }

    @Override
    public final AnnotationMetadata getAnnotationMetadata() {
        return method.getAnnotationMetadata();
    }
}



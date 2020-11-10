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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.TransactionalAdvice;
import io.micronaut.transaction.interceptor.DefaultTransactionAttribute;
import io.micronaut.transaction.support.DefaultTransactionDefinition;

import java.time.Duration;
import java.util.Optional;

/**
 * Used as a super class to resolve and potentially cache data about a method.
 *
 * @param <R> The return type.
 * @since 2.2.0
 * @author graemerocher
 */
public class DefaultStoredDataOperation<R> implements StoredDataOperation<R> {
    public static final DefaultTransactionDefinition NO_TRANSACTION = new DefaultTransactionDefinition();
    private final ExecutableMethod<?, ?> method;
    private TransactionDefinition transactionDefinition;

    public DefaultStoredDataOperation(ExecutableMethod<?, ?> method) {
        this.method = method;
    }

    @NonNull
    @Override
    public final Optional<TransactionDefinition> getTransactionDefinition() {
        if (transactionDefinition == null) {
            AnnotationValue<TransactionalAdvice> annotation = method.getAnnotation(TransactionalAdvice.class);

            if (annotation != null) {

                DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();
                attribute.setName(method.getDeclaringType().getSimpleName() + "." + method.getMethodName());
                attribute.setReadOnly(annotation.isTrue("readOnly"));
                annotation.intValue("timeout").ifPresent(value -> attribute.setTimeout(Duration.ofSeconds(value)));
                final Class[] noRollbackFors = annotation.classValues("noRollbackFor");
                //noinspection unchecked
                attribute.setNoRollbackFor(noRollbackFors);
                annotation.enumValue("propagation", TransactionDefinition.Propagation.class)
                        .ifPresent(attribute::setPropagationBehavior);
                annotation.enumValue("isolation", TransactionDefinition.Isolation.class)
                        .ifPresent(attribute::setIsolationLevel);
                this.transactionDefinition = attribute;
            } else {
                transactionDefinition = NO_TRANSACTION;
            }
        }
        if (transactionDefinition != NO_TRANSACTION) {
            return Optional.of(transactionDefinition);
        }
        return Optional.empty();
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



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
package io.micronaut.transaction.interceptor;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.kotlin.KotlinInterceptedMethod;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionOperationsRegistry;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.TransactionalAdvice;
import io.micronaut.transaction.async.AsyncTransactionOperations;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.support.TransactionUtil;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link TransactionalAdvice}. Forked from the reflection based code in Spring.
 *
 * @author graemerocher
 * @author Denis stepanov
 * @since 1.0
 */
@Singleton
@Internal
public final class TransactionalInterceptor implements MethodInterceptor<Object, Object> {
    /**
     * Holder to support the {@code currentTransactionStatus()} method,
     * and to support communication between different cooperating advices
     * (e.g. before and after advice) if the aspect involves more than a
     * single method (as will be the case for around advice).
     */
    private static final ThreadLocal<TransactionInfo> TRANSACTION_INFO_HOLDER =
            new ThreadLocal<TransactionInfo>() {
                @Override
                public String toString() {
                    return "Current aspect-driven transaction";
                }
            };
    private final Map<TenantExecutableMethod, TransactionInvocation> transactionInvocationMap = new ConcurrentHashMap<>(30);

    @NonNull
    private final TransactionOperationsRegistry transactionOperationsRegistry;
    @Nullable
    private final TransactionDataSourceTenantResolver tenantResolver;

    /**
     * Default constructor.
     *
     * @param transactionOperationsRegistry The {@link TransactionOperationsRegistry}
     * @param tenantResolver
     */
    public TransactionalInterceptor(@NonNull TransactionOperationsRegistry transactionOperationsRegistry,
                                    @Nullable TransactionDataSourceTenantResolver tenantResolver) {
        this.transactionOperationsRegistry = transactionOperationsRegistry;
        this.tenantResolver = tenantResolver;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRANSACTION.getPosition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        String tenantDataSourceName;
        if (tenantResolver != null) {
            tenantDataSourceName = tenantResolver.resolveTenantDataSourceName();
        } else {
            tenantDataSourceName = null;
        }
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
        try {
            ExecutableMethod<Object, Object> executableMethod = context.getExecutableMethod();
            final TransactionInvocation<?> transactionInvocation = transactionInvocationMap
                    .computeIfAbsent(new TenantExecutableMethod(tenantDataSourceName, executableMethod), ignore -> {
                        final String dataSource = tenantDataSourceName == null ? executableMethod.stringValue(TransactionalAdvice.class).orElse(null) : tenantDataSourceName;
                        final TransactionDefinition transactionDefinition = resolveTransactionDefinition(executableMethod);

                        switch (interceptedMethod.resultType()) {
                            case PUBLISHER:
                                ReactiveTransactionOperations<?> reactiveTransactionOperations = transactionOperationsRegistry.provideReactive(ReactiveTransactionOperations.class, dataSource);
                                return new TransactionInvocation<>(null, reactiveTransactionOperations, null, transactionDefinition);
                            case COMPLETION_STAGE:
                                AsyncTransactionOperations<?> asyncTransactionOperations = transactionOperationsRegistry.provideAsync(AsyncTransactionOperations.class, dataSource);
                                return new TransactionInvocation<>(null, null, asyncTransactionOperations, transactionDefinition);
                            default:
                                TransactionOperations<?> transactionManager = transactionOperationsRegistry.provideSynchronous(SynchronousTransactionManager.class, dataSource);
                                return new TransactionInvocation<>(transactionManager, null, null, transactionDefinition);
                        }
                    });

            final TransactionDefinition definition = transactionInvocation.definition;
            switch (interceptedMethod.resultType()) {
                case PUBLISHER:
                    ReactiveTransactionOperations<?> reactiveTransactionOperations = Objects.requireNonNull(transactionInvocation.reactiveTransactionOperations);
                    return interceptedMethod.handleResult(
                            reactiveTransactionOperations.withTransaction(definition, (status) -> {
                                context.setAttribute(ReactiveTransactionStatus.STATUS, status);
                                context.setAttribute(ReactiveTransactionStatus.ATTRIBUTE, definition);
                                return Publishers.convertPublisher(context.proceed(), Publisher.class);
                            })
                    );
                case COMPLETION_STAGE:
                    AsyncTransactionOperations<?> asyncTransactionOperations = Objects.requireNonNull(transactionInvocation.asyncTransactionOperations);
                    boolean isKotlinSuspended = interceptedMethod instanceof KotlinInterceptedMethod;
                    CompletionStage<?> result;
                    if (isKotlinSuspended) {
                        KotlinInterceptedMethod kotlinInterceptedMethod = (KotlinInterceptedMethod) interceptedMethod;
                        result = asyncTransactionOperations.withTransaction(definition, new KotlinInterceptedMethodAsyncResultSupplier<>(kotlinInterceptedMethod));
                    } else {
                        result = asyncTransactionOperations.withTransaction(definition, status -> interceptedMethod.interceptResultAsCompletionStage());
                    }
                    return interceptedMethod.handleResult(result);
                case SYNCHRONOUS:
                    TransactionOperations<?> transactionManager = Objects.requireNonNull(transactionInvocation.transactionManager);
                    return transactionManager.execute(definition, status -> {
                        TransactionInfo prev = TRANSACTION_INFO_HOLDER.get();
                        try {
                            TransactionInfo<?> transactionInfo = new TransactionInfo<>(definition, status);
                            TRANSACTION_INFO_HOLDER.set(transactionInfo);
                            return context.proceed();
                        } finally {
                            if (prev == null) {
                                TRANSACTION_INFO_HOLDER.remove();
                            } else {
                                TRANSACTION_INFO_HOLDER.set(prev);
                            }
                        }
                    });
                default:
                    return interceptedMethod.unsupported();

            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    /**
     * Return the transaction status of the current method invocation.
     * Mainly intended for code that wants to set the current transaction
     * rollback-only but not throw an application exception.
     *
     * @param <T> The connection type
     * @return The current status
     * @throws NoTransactionException if the transaction info cannot be found,
     *                                because the method was invoked outside an AOP invocation context
     */
    public static <T> TransactionStatus<T> currentTransactionStatus() throws NoTransactionException {
        TransactionInfo<T> info = TRANSACTION_INFO_HOLDER.get();
        if (info == null) {
            throw new NoTransactionException("No transaction aspect-managed TransactionStatus in scope");
        }
        return info.transactionStatus;
    }

    /**
     * @param executableMethod The method
     * @return The {@link TransactionDefinition}
     * @deprecated The class will be final with private methods in the next major version
     */
    private TransactionDefinition resolveTransactionDefinition(ExecutableMethod<Object, Object> executableMethod) {
        TransactionDefinition definition = TransactionUtil.getTransactionDefinition(
                executableMethod.getDeclaringType().getSimpleName() + "." + executableMethod.getMethodName(), executableMethod);
        if (definition == TransactionDefinition.DEFAULT) {
            throw new IllegalStateException("No declared @Transactional annotation present");
        }
        return definition;
    }

    /**
     * Cached invocation associating a method with a definition a transaction manager.
     *
     * @param <C> connection type
     */
    private static final class TransactionInvocation<C> {
        @Nullable
        final TransactionOperations<C> transactionManager;
        @Nullable
        final ReactiveTransactionOperations<C> reactiveTransactionOperations;
        @Nullable
        final AsyncTransactionOperations<C> asyncTransactionOperations;
        final TransactionDefinition definition;

        TransactionInvocation(@Nullable TransactionOperations<C> transactionManager,
                              @Nullable ReactiveTransactionOperations<C> reactiveTransactionOperations,
                              @Nullable AsyncTransactionOperations<C> asyncTransactionOperations,
                              TransactionDefinition definition) {
            this.transactionManager = transactionManager;
            this.reactiveTransactionOperations = reactiveTransactionOperations;
            this.asyncTransactionOperations = asyncTransactionOperations;
            this.definition = definition;
        }

    }

    private static final class TransactionInfo<T> {

        private final TransactionDefinition transactionDefinition;
        private final TransactionStatus<T> transactionStatus;

        private TransactionInfo(TransactionDefinition transactionDefinition, TransactionStatus<T> transactionStatus) {
            this.transactionDefinition = transactionDefinition;
            this.transactionStatus = transactionStatus;
        }
    }

    private static final class TenantExecutableMethod {
        private final String dataSource;
        private final ExecutableMethod method;
        private final int hashCode;

        TenantExecutableMethod(String dataSource, ExecutableMethod method) {
            this.dataSource = dataSource;
            this.method = method;
            this.hashCode = Objects.hash(dataSource, method);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TenantExecutableMethod that = (TenantExecutableMethod) o;
            return Objects.equals(dataSource, that.dataSource) && method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}

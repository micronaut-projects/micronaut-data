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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionOperationsRegistry;
import io.micronaut.transaction.annotation.TransactionalAdvice;
import io.micronaut.transaction.async.AsyncTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.micronaut.transaction.support.TransactionUtil;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
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

    private final Map<TenantExecutableMethod, TransactionInvocation> transactionInvocationMap = new ConcurrentHashMap<>(30);

    @NonNull
    private final TransactionOperationsRegistry transactionOperationsRegistry;
    @Nullable
    private final TransactionDataSourceTenantResolver tenantResolver;

    private final ConversionService conversionService;

    /**
     * Default constructor.
     *
     * @param transactionOperationsRegistry The {@link TransactionOperationsRegistry}
     * @param tenantResolver                The {@link TransactionDataSourceTenantResolver}
     * @param conversionService             The conversion service
     */
    public TransactionalInterceptor(@NonNull TransactionOperationsRegistry transactionOperationsRegistry,
                                    @Nullable TransactionDataSourceTenantResolver tenantResolver, ConversionService conversionService) {
        this.transactionOperationsRegistry = transactionOperationsRegistry;
        this.tenantResolver = tenantResolver;
        this.conversionService = conversionService;
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
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            ExecutableMethod<Object, Object> executableMethod = context.getExecutableMethod();
            final TransactionInvocation<?> transactionInvocation = transactionInvocationMap
                .computeIfAbsent(new TenantExecutableMethod(tenantDataSourceName, executableMethod), ignore -> {
                    final String dataSource = tenantDataSourceName == null ? executableMethod.stringValue(TransactionalAdvice.class).orElse(null) : tenantDataSourceName;
                    final TransactionDefinition transactionDefinition = resolveTransactionDefinition(executableMethod);

                    switch (interceptedMethod.resultType()) {
                        case PUBLISHER -> {
                            ReactiveTransactionOperations<?> reactiveTransactionOperations = transactionOperationsRegistry.provideReactive(ReactiveTransactionOperations.class, dataSource);
                            return new TransactionInvocation<>(null, reactiveTransactionOperations, null, transactionDefinition);
                        }
                        case COMPLETION_STAGE -> {
                            AsyncTransactionOperations<?> asyncTransactionOperations = transactionOperationsRegistry.provideAsync(AsyncTransactionOperations.class, dataSource);
                            return new TransactionInvocation<>(null, null, asyncTransactionOperations, transactionDefinition);
                        }
                        default -> {
                            TransactionOperations<?> transactionManager = transactionOperationsRegistry.provideSynchronous(TransactionOperations.class, dataSource);
                            return new TransactionInvocation<>(transactionManager, null, null, transactionDefinition);
                        }
                    }
                });

            final TransactionDefinition definition = transactionInvocation.definition;
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    ReactiveTransactionOperations<?> reactiveTransactionOperations = Objects.requireNonNull(transactionInvocation.reactiveTransactionOperations);
                    if (reactiveTransactionOperations instanceof ReactorReactiveTransactionOperations<?> reactorReactiveTransactionOperations) {
                        if (context.getReturnType().isSingleResult()) {
                            return interceptedMethod.handleResult(
                                reactorReactiveTransactionOperations.withTransactionMono(definition, status -> Mono.from(interceptedMethod.interceptResultAsPublisher()))
                            );
                        }
                        return interceptedMethod.handleResult(
                            reactorReactiveTransactionOperations.withTransactionFlux(definition, status -> Flux.from(interceptedMethod.interceptResultAsPublisher()))
                        );
                    }
                    return interceptedMethod.handleResult(
                        reactiveTransactionOperations.withTransaction(definition, (status) -> interceptedMethod.interceptResultAsPublisher())
                    );
                }
                case COMPLETION_STAGE -> {
                    AsyncTransactionOperations<?> asyncTransactionOperations = Objects.requireNonNull(transactionInvocation.asyncTransactionOperations);
                    return interceptedMethod.handleResult(
                        asyncTransactionOperations.withTransaction(definition, status -> interceptedMethod.interceptResultAsCompletionStage())
                    );
                }
                case SYNCHRONOUS -> {
                    TransactionOperations<?> transactionManager = Objects.requireNonNull(transactionInvocation.transactionManager);
                    return transactionManager.execute(definition, status -> context.proceed());
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    /**
     * @param executableMethod The method
     * @return The {@link TransactionDefinition}
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
     * @param transactionManager            The transaction manager
     * @param reactiveTransactionOperations The reactive transaction manager
     * @param asyncTransactionOperations    The async transaction manager
     * @param definition                    The definition
     * @param <C>                           connection type
     */
    private record TransactionInvocation<C>(@Nullable TransactionOperations<C> transactionManager,
                                            @Nullable ReactiveTransactionOperations<C> reactiveTransactionOperations,
                                            @Nullable AsyncTransactionOperations<C> asyncTransactionOperations,
                                            TransactionDefinition definition) {

    }

    private record TenantExecutableMethod(String dataSource, ExecutableMethod method) {
    }
}

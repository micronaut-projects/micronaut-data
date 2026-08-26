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
import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionOperationsRegistry;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.annotation.Transactional;
import io.micronaut.transaction.async.AsyncTransactionOperations;
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.micronaut.transaction.support.TransactionUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link Transactional}. Forked from the reflection based code in Spring.
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
    private final RecoverableTransactionExecutor recoverableTransactionExecutor;
    private final SessionlessTransactionExecutor sessionlessTransactionExecutor;

    /**
     * Default constructor.
     *
     * @param transactionOperationsRegistry The {@link TransactionOperationsRegistry}
     * @param tenantResolver                The {@link TransactionDataSourceTenantResolver}
     * @param conversionService             The conversion service
     * @param beanLocator                   The bean locator
     */
    public TransactionalInterceptor(@NonNull TransactionOperationsRegistry transactionOperationsRegistry,
                                    @Nullable TransactionDataSourceTenantResolver tenantResolver,
                                    ConversionService conversionService,
                                    BeanLocator beanLocator) {
        this.transactionOperationsRegistry = transactionOperationsRegistry;
        this.tenantResolver = tenantResolver;
        this.conversionService = conversionService;
        this.recoverableTransactionExecutor = new RecoverableTransactionExecutor(beanLocator);
        this.sessionlessTransactionExecutor = new SessionlessTransactionExecutor(beanLocator);
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRANSACTION.getPosition();
    }

    @Override
    @Nullable
    @SuppressWarnings("NullAway")
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        @Nullable String tenantDataSourceName = resolveTenantDataSourceName();
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            ExecutableMethod<Object, Object> executableMethod = context.getExecutableMethod();
            @Nullable String dataSource = resolveDataSourceName(tenantDataSourceName, executableMethod);
            TransactionInvocation<?> transactionInvocation = resolveTransactionInvocation(tenantDataSourceName, executableMethod, interceptedMethod, dataSource);
            return switch (interceptedMethod.resultType()) {
                case PUBLISHER -> interceptPublisher(context, interceptedMethod, transactionInvocation);
                case COMPLETION_STAGE -> interceptCompletionStage(interceptedMethod, transactionInvocation);
                case SYNCHRONOUS -> interceptSynchronous(context, dataSource, transactionInvocation);
                default -> interceptedMethod.unsupported();
            };
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    @Nullable
    private String resolveTenantDataSourceName() {
        if (tenantResolver == null) {
            return null;
        }
        return tenantResolver.resolveTenantDataSourceName();
    }

    @Nullable
    private String resolveDataSourceName(@Nullable String tenantDataSourceName,
                                         ExecutableMethod<Object, Object> executableMethod) {
        if (tenantDataSourceName != null) {
            return tenantDataSourceName;
        }
        return executableMethod.stringValue(Transactional.class).orElse(null);
    }

    private TransactionInvocation<?> resolveTransactionInvocation(@Nullable String tenantDataSourceName,
                                                                  ExecutableMethod<Object, Object> executableMethod,
                                                                  InterceptedMethod interceptedMethod,
                                                                  @Nullable String dataSource) {
        return transactionInvocationMap.computeIfAbsent(
            new TenantExecutableMethod(tenantDataSourceName, executableMethod),
            ignore -> createTransactionInvocation(interceptedMethod, executableMethod, dataSource)
        );
    }

    private TransactionInvocation<?> createTransactionInvocation(InterceptedMethod interceptedMethod,
                                                                 ExecutableMethod<Object, Object> executableMethod,
                                                                 @Nullable String dataSource) {
        TransactionDefinition transactionDefinition = resolveTransactionDefinition(executableMethod);
        return switch (interceptedMethod.resultType()) {
            case PUBLISHER -> new TransactionInvocation<>(
                null,
                transactionOperationsRegistry.provideReactive(ReactiveTransactionOperations.class, dataSource),
                null,
                transactionDefinition
            );
            case COMPLETION_STAGE -> new TransactionInvocation<>(
                null,
                null,
                transactionOperationsRegistry.provideAsync(AsyncTransactionOperations.class, dataSource),
                transactionDefinition
            );
            default -> new TransactionInvocation<>(
                transactionOperationsRegistry.provideSynchronous(TransactionOperations.class, dataSource),
                null,
                null,
                transactionDefinition
            );
        };
    }

    @Nullable
    private Object interceptPublisher(MethodInvocationContext<Object, Object> context,
                                      InterceptedMethod interceptedMethod,
                                      TransactionInvocation<?> transactionInvocation) {
        TransactionDefinition definition = transactionInvocation.definition;
        rejectOracleSessionlessMode(definition, "reactive");
        ReactiveTransactionOperations<?> reactiveTransactionOperations = Objects.requireNonNull(transactionInvocation.reactiveTransactionOperations);
        if (reactiveTransactionOperations instanceof ReactorReactiveTransactionOperations<?> reactorTransactionOperations) {
            if (context.getReturnType().isSingleResult()) {
                return interceptedMethod.handleResult(
                    reactorTransactionOperations.withTransactionMono(definition, status -> Mono.from(interceptedMethod.interceptResultAsPublisher()))
                );
            }
            return interceptedMethod.handleResult(
                reactorTransactionOperations.withTransactionFlux(definition, status -> Flux.from(interceptedMethod.interceptResultAsPublisher()))
            );
        }
        return interceptedMethod.handleResult(
            reactiveTransactionOperations.withTransaction(definition, status -> interceptedMethod.interceptResultAsPublisher())
        );
    }

    @Nullable
    private Object interceptCompletionStage(InterceptedMethod interceptedMethod,
                                            TransactionInvocation<?> transactionInvocation) {
        TransactionDefinition definition = transactionInvocation.definition;
        rejectOracleSessionlessMode(definition, "CompletionStage");
        AsyncTransactionOperations<?> asyncTransactionOperations = Objects.requireNonNull(transactionInvocation.asyncTransactionOperations);
        return interceptedMethod.handleResult(
            asyncTransactionOperations.withTransaction(definition, status -> interceptedMethod.interceptResultAsCompletionStage())
        );
    }

    @Nullable
    private Object interceptSynchronous(MethodInvocationContext<Object, Object> context,
                                        @Nullable String dataSource,
                                        TransactionInvocation<?> transactionInvocation) {
        TransactionDefinition definition = transactionInvocation.definition;
        TransactionOperations<?> transactionManager = Objects.requireNonNull(transactionInvocation.transactionManager);
        OracleTransactional.Sessionless sessionless = TransactionUtil.getOracleSessionlessMode(definition);
        if (sessionless != null) {
            TransactionUtil.validateOracleSessionlessPropagation(definition);
            if (context.getAnnotationMetadata().hasAnnotation(OracleTransactional.Recoverable.class)) {
                throw new TransactionUsageException(
                    "Oracle sessionless transaction mode '" + sessionless + "' cannot be combined with @OracleTransactional.Recoverable"
                );
            }
            return sessionlessTransactionExecutor.execute(
                transactionManager,
                definition,
                context,
                sessionless,
                dataSource
            );
        }
        if (context.getAnnotationMetadata().hasAnnotation(OracleTransactional.Recoverable.class)) {
            return recoverableTransactionExecutor.execute(
                transactionManager,
                definition,
                context,
                dataSource
            );
        }
        return transactionManager.<@Nullable Object>execute(definition, status -> context.proceed());
    }

    private static void rejectOracleSessionlessMode(TransactionDefinition definition, String resultType) {
        OracleTransactional.Sessionless sessionless = TransactionUtil.getOracleSessionlessMode(definition);
        if (sessionless != null) {
            throw new TransactionSuspensionNotSupportedException(
                "Oracle sessionless transaction mode '" + sessionless + "' is not supported on " + resultType
                    + " methods; sessionless transactions are only supported on synchronous methods"
            );
        }
    }

    /**
     * @param executableMethod The method
     * @return The {@link TransactionDefinition}
     */
    private TransactionDefinition resolveTransactionDefinition(ExecutableMethod<Object, Object> executableMethod) {
        String name = executableMethod.stringValue(Transactional.class, "name")
            .orElseGet(() -> executableMethod.getDeclaringType().getSimpleName() + "." + executableMethod.getMethodName());
        TransactionDefinition definition = TransactionUtil.getTransactionDefinition(name, executableMethod);
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

    private record TenantExecutableMethod(@Nullable String dataSource, ExecutableMethod method) {
    }
}

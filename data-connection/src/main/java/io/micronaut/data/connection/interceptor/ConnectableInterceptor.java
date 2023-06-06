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
package io.micronaut.data.connection.interceptor;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionOperationsRegistry;
import io.micronaut.data.connection.DefaultConnectionDefinition;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.data.connection.async.AsyncConnectionOperations;
import io.micronaut.data.connection.reactive.ReactiveStreamsConnectionOperations;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link Connectable} interceptor.
 *
 * @author Denis stepanov
 * @since 4.0.0
 */
@Internal
@Singleton
@InterceptorBean(Connectable.class)
public final class ConnectableInterceptor implements MethodInterceptor<Object, Object> {

    private final Map<TenantExecutableMethod, ConnectionInvocation> connectionInvocationMap = new ConcurrentHashMap<>(30);

    @NonNull
    private final ConnectionOperationsRegistry connectionOperationsRegistry;
    @Nullable
    private final ConnectionDataSourceTenantResolver tenantResolver;

    private final ConversionService conversionService;

    /**
     * Default constructor.
     *
     * @param connectionOperationsRegistry The {@link ConnectionOperationsRegistry}
     * @param tenantResolver               The tenant resolver
     * @param conversionService            The conversion service
     */
    ConnectableInterceptor(@NonNull ConnectionOperationsRegistry connectionOperationsRegistry,
                           @Nullable ConnectionDataSourceTenantResolver tenantResolver,
                           ConversionService conversionService) {
        this.connectionOperationsRegistry = connectionOperationsRegistry;
        this.tenantResolver = tenantResolver;
        this.conversionService = conversionService;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRANSACTION.getPosition() - 10;
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
            final ConnectionInvocation connectionInvocation = connectionInvocationMap
                .computeIfAbsent(new TenantExecutableMethod(tenantDataSourceName, executableMethod), ignore -> {
                    final String dataSource = tenantDataSourceName == null ? executableMethod.stringValue(Connectable.class).orElse(null) : tenantDataSourceName;
                    final ConnectionDefinition connectionDefinition = getConnectionDefinition(executableMethod);

                    switch (interceptedMethod.resultType()) {
                        case PUBLISHER -> {
                            ReactiveStreamsConnectionOperations<?> operations = connectionOperationsRegistry.provideReactive(ReactiveStreamsConnectionOperations.class, dataSource);
                            return new ConnectionInvocation(null, operations, null, connectionDefinition);
                        }
                        case COMPLETION_STAGE -> {
                            AsyncConnectionOperations<?> operations = connectionOperationsRegistry.provideAsync(AsyncConnectionOperations.class, dataSource);
                            return new ConnectionInvocation(null, null, operations, connectionDefinition);
                        }
                        default -> {
                            ConnectionOperations<?> operations = connectionOperationsRegistry.provideSynchronous(ConnectionOperations.class, dataSource);
                            return new ConnectionInvocation(operations, null, null, connectionDefinition);
                        }
                    }
                });

            final ConnectionDefinition definition = connectionInvocation.definition;
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    ReactiveStreamsConnectionOperations<?> operations = Objects.requireNonNull(connectionInvocation.reactiveStreamsConnectionOperations);
                    if (connectionInvocation.reactorConnectionOperations != null) {
                        ReactorConnectionOperations<?> reactorConnectionOperations = connectionInvocation.reactorConnectionOperations;
                        if (context.getExecutableMethod().getReturnType().isSingleResult()) {
                            return reactorConnectionOperations.withConnectionMono(definition, status -> Mono.from(interceptedMethod.interceptResultAsPublisher()));
                        }
                        return reactorConnectionOperations.withConnectionFlux(definition, status -> Flux.from(interceptedMethod.interceptResultAsPublisher()));
                    }
                    return interceptedMethod.handleResult(
                        operations.withConnection(definition, status -> interceptedMethod.interceptResultAsPublisher())
                    );
                }
                case COMPLETION_STAGE -> {
                    AsyncConnectionOperations<?> operations = Objects.requireNonNull(connectionInvocation.asyncConnectionOperations);
                    return interceptedMethod.handleResult(
                        operations.withConnection(definition, status -> interceptedMethod.interceptResultAsCompletionStage())
                    );
                }
                case SYNCHRONOUS -> {
                    ConnectionOperations<?> operations = Objects.requireNonNull(connectionInvocation.connectionOperations);
                    return operations.execute(definition, connection -> context.proceed());
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    @NonNull
    public static ConnectionDefinition getConnectionDefinition(ExecutableMethod<Object, Object> executableMethod) {
        AnnotationValue<Connectable> annotation = executableMethod.getAnnotation(Connectable.class);
        if (annotation == null) {
            throw new IllegalStateException("No declared @Connectable annotation present");
        }

        return new DefaultConnectionDefinition(
            executableMethod.getDeclaringType().getSimpleName() + "." + executableMethod.getMethodName(),
            annotation.enumValue("propagation", ConnectionDefinition.Propagation.class).orElse(ConnectionDefinition.PROPAGATION_DEFAULT),
            annotation.longValue("timeout").stream().mapToObj(Duration::ofSeconds).findFirst().orElse(null),
            annotation.booleanValue("readOnly").orElse(null)
        );
    }

    /**
     * Cached invocation associating a method with a definition a connection manager.
     *
     * @param connectionOperations                The connection operations
     * @param reactorConnectionOperations         The reactor connection operations
     * @param reactiveStreamsConnectionOperations The reactive connection operations
     * @param asyncConnectionOperations           The async connection operations
     * @param definition                          The connection definition
     */
    private record ConnectionInvocation(
        @Nullable ConnectionOperations<?> connectionOperations,
        @Nullable ReactorConnectionOperations<?> reactorConnectionOperations,
        @Nullable ReactiveStreamsConnectionOperations<?> reactiveStreamsConnectionOperations,
        @Nullable AsyncConnectionOperations<?> asyncConnectionOperations,
        ConnectionDefinition definition) {

        ConnectionInvocation(
            @Nullable ConnectionOperations<?> connectionOperations,
            @Nullable ReactiveStreamsConnectionOperations<?> reactiveStreamsConnectionOperations,
            @Nullable AsyncConnectionOperations<?> asyncConnectionOperations, ConnectionDefinition definition) {

            this(connectionOperations,
                reactiveStreamsConnectionOperations instanceof ReactorConnectionOperations<?> reactorReactiveConnectionOperations ? reactorReactiveConnectionOperations : null,
                reactiveStreamsConnectionOperations,
                asyncConnectionOperations,
                definition);
        }
    }

    /**
     * The tenant executable method.
     *
     * @param dataSource The datasource name
     * @param method     The method
     */
    private record TenantExecutableMethod(String dataSource, ExecutableMethod<?, ?> method) {
    }

}

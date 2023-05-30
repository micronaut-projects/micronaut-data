/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.session;

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.reactive.ReactiveConnectionOperations;
import io.micronaut.data.connection.manager.reactive.ReactorReactiveConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.support.DefaultConnectionStatus;
import io.micronaut.data.mongodb.conf.RequiresReactiveMongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The reactive MongoDB connection operations implementation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@RequiresReactiveMongo
@EachBean(MongoClient.class)
@Internal
final class DefaultReactiveMongoSessionOperations implements ReactorReactiveConnectionOperations<ClientSession>, MongoReactorReactiveConnectionOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveMongoSessionOperations.class);

    private final String serverName;
    private final MongoClient mongoClient;

    DefaultReactiveMongoSessionOperations(@Parameter String serverName, MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        this.serverName = serverName;
    }

    @Override
    public Optional<ConnectionStatus<ClientSession>> findConnectionStatus(ContextView contextView) {
        return ReactorPropagation.findAllContextElements(contextView, ClientSessionPropagatedContext.class)
            .filter(e -> e.connectionOperations == this)
            .map(ClientSessionPropagatedContext::status)
            .findFirst();
    }

    @NonNull
    @Override
    public <T> Flux<T> withConnectionFlux(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<ClientSession>, Flux<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Flux.deferContextual(contextView -> {
            ClientSession clientSession = findClientSession(contextView);
            if (clientSession != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY -> callback.apply(new DefaultConnectionStatus<>(clientSession, definition, false));
                    case REQUIRES_NEW -> //                    yield suspend(clientSession, () -> executeWithNewConnection(definition, callback));
                        callback.apply(new DefaultConnectionStatus<>(clientSession, definition, false));
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> Flux.usingWhen(
                    LOG.isDebugEnabled() ? Flux.from(mongoClient.startSession()).doOnNext(cs -> LOG.debug("Opening Connection for MongoDB configuration: {} and definition: {}", serverName, definition)) : mongoClient.startSession(),
                    cs -> {
                        DefaultConnectionStatus<ClientSession> status = new DefaultConnectionStatus<>(cs, definition, true);
                        return callback.apply(status).contextWrite(ctx -> addClientSession(ctx, status));
                    },
                    connection -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Closing Connection for MongoDB configuration: {} and definition: {}", serverName, definition);
                        }
                        connection.close();
                        return Flux.empty();
                    }
                );
                case MANDATORY ->
                    throw new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
            };
        });
    }

    @NonNull
    @Override
    public <T> Mono<T> withConnectionMono(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<ClientSession>, Mono<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Mono.deferContextual(contextView -> {
            ClientSession clientSession = findClientSession(contextView);
            if (clientSession != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY -> callback.apply(new DefaultConnectionStatus<>(clientSession, definition, false));
                    case REQUIRES_NEW -> //                    yield suspend(clientSession, () -> executeWithNewConnection(definition, callback));
                        callback.apply(new DefaultConnectionStatus<>(clientSession, definition, false));
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> Mono.usingWhen(
                    LOG.isDebugEnabled() ? Mono.from(mongoClient.startSession()).doOnNext(cs -> LOG.debug("Opening Connection for MongoDB configuration: {} and definition: {}", serverName, definition)) : mongoClient.startSession(),
                    cs -> {
                        DefaultConnectionStatus<ClientSession> status = new DefaultConnectionStatus<>(cs, definition, true);
                        return callback.apply(status)
                            .contextWrite(ctx -> addClientSession(ctx, status));
                    },
                    connection -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Closing Connection for MongoDB configuration: {} and definition: {}", serverName, definition);
                        }
                        connection.close();
                        return Mono.empty();
                    }
                );
                case MANDATORY ->
                    throw new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
            };
        });
    }

    @NonNull
    private Context addClientSession(@NonNull Context context, @NonNull ConnectionStatus<ClientSession> status) {
        return ReactorPropagation.addContextElement(
            context,
            new ClientSessionPropagatedContext(this, status)
        );
    }

    @Nullable
    private ClientSession findClientSession(@NonNull ContextView contextView) {
        return findConnectionStatus(contextView)
            .map(ConnectionStatus::getConnection)
            .orElse(null);
    }

    private record ClientSessionPropagatedContext(
        ReactiveConnectionOperations<?> connectionOperations,
        ConnectionStatus<ClientSession> status)
        implements PropagatedContextElement {
    }
}

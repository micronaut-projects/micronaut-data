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
package io.micronaut.data.mongodb.operations;

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
import io.micronaut.data.mongodb.conf.RequiresReactiveMongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.function.Function;

/**
 * The reactive MongoDB connection operations implementation.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@RequiresReactiveMongo
@EachBean(MongoClient.class)
@Internal
public class DefaultReactiveMongoConnectionOperations implements ReactorReactiveConnectionOperations<ClientSession> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveMongoConnectionOperations.class);

    private final String serverName;
    private final MongoClient mongoClient;

    public DefaultReactiveMongoConnectionOperations(@Parameter String serverName, MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        this.serverName = serverName;
        String name = serverName;
        if (name == null) {
            name = "default";
        }
    }

    @NonNull
    @Override
    public <T> Flux<T> withConnectionFlux(@NonNull ConnectionDefinition definition, @NonNull Function<ClientSession, Flux<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Flux.deferContextual(contextView -> {
            ClientSession clientSession = findClientSession(contextView);
            if (clientSession != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY -> callback.apply(clientSession);
                    case REQUIRES_NEW -> //                    yield suspend(clientSession, () -> executeWithNewConnection(definition, callback));
                        callback.apply(clientSession);
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> Flux.usingWhen(
                    LOG.isDebugEnabled() ? Flux.from(mongoClient.startSession()).doOnNext(cs -> LOG.debug("Opening Connection for MongoDB configuration: {} and definition: {}", serverName, definition)) : mongoClient.startSession(),
                    cs -> callback.apply(cs).contextWrite(ctx -> addClientSession(ctx, cs)),
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
    public <T> Mono<T> withConnectionMono(@NonNull ConnectionDefinition definition, @NonNull Function<ClientSession, Mono<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Mono.deferContextual(contextView -> {
            ClientSession clientSession = findClientSession(contextView);
            if (clientSession != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY -> callback.apply(clientSession);
                    case REQUIRES_NEW -> //                    yield suspend(clientSession, () -> executeWithNewConnection(definition, callback));
                        callback.apply(clientSession);
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> Mono.usingWhen(
                    LOG.isDebugEnabled() ? Mono.from(mongoClient.startSession()).doOnNext(cs -> LOG.debug("Opening Connection for MongoDB configuration: {} and definition: {}", serverName, definition)) : mongoClient.startSession(),
                    cs -> callback.apply(cs).contextWrite(ctx -> addClientSession(ctx, cs)),
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
    private Context addClientSession(@NonNull Context context, @NonNull ClientSession clientSession) {
        return ReactorPropagation.addContextElement(
            context,
            new ClientSessionPropagatedContext(this, clientSession)
        );
    }

    @Nullable
    private ClientSession findClientSession(@NonNull ContextView contextView) {
        return ReactorPropagation.findAllContextElements(contextView, ClientSessionPropagatedContext.class)
            .filter(e -> e.connectionOperations == this)
            .map(ClientSessionPropagatedContext::clientSession)
            .findFirst()
            .orElse(null);
    }

    private record ClientSessionPropagatedContext(
        ReactiveConnectionOperations<?> connectionOperations,
        ClientSession clientSession)
        implements PropagatedContextElement {
    }
}

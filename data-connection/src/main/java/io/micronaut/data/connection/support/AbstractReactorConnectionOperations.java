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
package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.reactive.ReactiveStreamsConnectionOperations;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
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
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractReactorConnectionOperations<C> implements ReactorConnectionOperations<C> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractReactorConnectionOperations.class);

    private static final Subscriber<Void> CLOSE_CONNECTION_SUBSCRIBER = new Subscriber<>() {
        @Override
        public void onSubscribe(Subscription s) {

        }

        @Override
        public void onNext(Void unused) {

        }

        @Override
        public void onError(Throwable t) {
            LOG.debug("Failed to close the connection", t);
        }

        @Override
        public void onComplete() {
        }
    };

    /**
     * Open a new connection.
     *
     * @param definition The connection definition
     * @return new connection publisher
     */
    @NonNull
    protected abstract Publisher<C> openConnection(@NonNull ConnectionDefinition definition);

    /**
     * Close the connection.
     *
     * @param connection The connection
     * @param definition The connection definition
     * @return closed publisher
     */
    @NonNull
    protected abstract Publisher<Void> closeConnection(@NonNull C connection, @NonNull ConnectionDefinition definition);

    @Override
    public final Optional<ConnectionStatus<C>> findConnectionStatus(@NonNull ContextView contextView) {
        return findPropagateContextElement(contextView)
            .map(e -> (ConnectionStatus<C>) e.status);
    }

    private Optional<ClientSessionPropagatedContext> findPropagateContextElement(ContextView contextView) {
        return ReactorPropagation.findAllContextElements(contextView, ClientSessionPropagatedContext.class)
            .filter(e -> e.connectionOperations == this)
            .findFirst();
    }

    @NonNull
    @Override
    public <T> Flux<T> withConnectionFlux(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<C>, Flux<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Flux.deferContextual(contextView -> {
            C connection = findConnection(contextView);
            if (connection != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY ->
                        existingConnectionFlux(definition, callback, connection);
                    case REQUIRES_NEW -> openConnectionFlux(definition, callback);
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> openConnectionFlux(definition, callback);
                case MANDATORY -> throw noConnectionFound();
            };
        });
    }

    private <T> Flux<T> existingConnectionFlux(ConnectionDefinition definition, Function<ConnectionStatus<C>, Flux<T>> callback, C clientSession) {
        return callback.apply(new DefaultConnectionStatus<>(clientSession, definition, false));
    }

    private <T> Flux<T> openConnectionFlux(ConnectionDefinition definition, Function<ConnectionStatus<C>, Flux<T>> callback) {
        return Mono.from(openConnection(definition))
            .flatMapMany(connection -> {
                DefaultConnectionStatus<C> status = new DefaultConnectionStatus<>(connection, definition, true);
                return callback.apply(status).contextWrite(ctx -> addClientSession(ctx, status))
                    .doAfterTerminate(() -> closeConnection(connection, definition).subscribe(CLOSE_CONNECTION_SUBSCRIBER));
            });
    }

    @NonNull
    @Override
    public <T> Mono<T> withConnectionMono(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<C>, Mono<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Mono.deferContextual(contextView -> {
            C connection = findConnection(contextView);
            if (connection != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY ->
                        existingConnectionMono(definition, callback, connection);
                    case REQUIRES_NEW -> openConnectionMono(definition, callback);
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> openConnectionMono(definition, callback);
                case MANDATORY -> throw noConnectionFound();
            };
        });
    }

    private <T> Mono<T> existingConnectionMono(ConnectionDefinition definition, Function<ConnectionStatus<C>, Mono<T>> callback, C clientSession) {
        return callback.apply(new DefaultConnectionStatus<>(clientSession, definition, false));
    }

    private <T> Mono<T> openConnectionMono(ConnectionDefinition definition, Function<ConnectionStatus<C>, Mono<T>> callback) {
        return Mono.usingWhen(
            openConnection(definition),
            connection -> {
                DefaultConnectionStatus<C> status = new DefaultConnectionStatus<>(connection, definition, true);
                return callback.apply(status).contextWrite(ctx -> addClientSession(ctx, status));
            },
            connection -> closeConnection(connection, definition)
        );
    }

    private NoConnectionException noConnectionFound() {
        return new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
    }

    @NonNull
    private Context addClientSession(@NonNull Context context, @NonNull ConnectionStatus<C> status) {
        return ReactorPropagation.addContextElement(
            context,
            new ClientSessionPropagatedContext<>(this, status)
        );
    }

    @Nullable
    private C findConnection(@NonNull ContextView contextView) {
        return findConnectionStatus(contextView)
            .map(ConnectionStatus::getConnection)
            .orElse(null);
    }

    private record ClientSessionPropagatedContext<C>(
        ReactiveStreamsConnectionOperations<?> connectionOperations,
        ConnectionStatus<C> status)
        implements PropagatedContextElement {
    }
}

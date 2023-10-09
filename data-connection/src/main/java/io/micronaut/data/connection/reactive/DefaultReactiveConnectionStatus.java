/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.connection.reactive;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionSynchronization;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The default reactive connection status.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class DefaultReactiveConnectionStatus<C> implements ReactiveConnectionStatus<C> {

    private static final Mono<Void> EMPTY = Mono.empty();

    private final C connection;
    private final ConnectionDefinition definition;
    private final boolean isNew;

    private List<ReactiveConnectionSynchronization> connectionSynchronizations;

    public DefaultReactiveConnectionStatus(C connection, ConnectionDefinition definition, boolean isNew) {
        this.connection = connection;
        this.definition = definition;
        this.isNew = isNew;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public C getConnection() {
        return connection;
    }

    @Override
    public ConnectionDefinition getDefinition() {
        return definition;
    }

    @Override
    public void registerSynchronization(ConnectionSynchronization synchronization) {
        registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
            @Override
            public Publisher<Void> onComplete() {
                return Mono.defer(() -> {
                    synchronization.executionComplete();
                    return Mono.empty();
                });
            }

            @Override
            public Publisher<Void> onClose() {
                return Mono.defer(() -> {
                    synchronization.beforeClosed();
                    return Mono.empty();
                });
            }

            @Override
            public Publisher<Void> afterClose() {
                return Mono.defer(() -> {
                    synchronization.afterClosed();
                    return Mono.empty();
                });
            }
        });
    }

    @Override
    public void registerReactiveSynchronization(ReactiveConnectionSynchronization synchronization) {
        if (connectionSynchronizations == null) {
            connectionSynchronizations = new ArrayList<>(5);
        }
        OrderUtil.sort(connectionSynchronizations);
        connectionSynchronizations.add(synchronization);
    }

    private Publisher<Void> forEachSynchronizations(Function<ReactiveConnectionSynchronization, Publisher<Void>> consumer) {
        if (connectionSynchronizations == null) {
            return Mono.empty();
        }
        Mono<Void> chain = Mono.empty();
        ListIterator<ReactiveConnectionSynchronization> listIterator = connectionSynchronizations.listIterator(connectionSynchronizations.size());
        while (listIterator.hasPrevious()) {
            Publisher<Void> next = consumer.apply(listIterator.previous());
            if (next != EMPTY) {
                chain = chain.then(Mono.from(next));
            }
        }
        return chain;
    }

    public Publisher<Void> onComplete(Supplier<Publisher<Void>> closeConnection) {
        return finallyBlock(onComplete(), null, throwable -> finallyComplete(throwable, closeConnection));
    }

    public Publisher<Void> onError(Throwable outterThrowable, Supplier<Publisher<Void>> closeConnection) {
        return finallyBlock(onError(outterThrowable), outterThrowable, throwable -> finallyComplete(throwable, closeConnection));
    }

    public Publisher<Void> onCancel(Supplier<Publisher<Void>> closeConnection) {
        return finallyBlock(onCancel(), null, throwable -> finallyComplete(throwable, closeConnection));
    }

    private Mono<Void> finallyComplete(@Nullable Throwable outterThrowable, Supplier<Publisher<Void>> closeConnection) {
        return finallyBlock(beforeClosed(), outterThrowable, throwable -> finallyBeforeClosed(throwable, closeConnection));
    }

    private Mono<Void> finallyBeforeClosed(@Nullable Throwable outterThrowable, Supplier<Publisher<Void>> closeConnection) {
        return finallyBlock(closeConnection.get(), outterThrowable, this::finallyClose);
    }

    private Mono<Void> finallyClose(@Nullable Throwable outterThrowable) {
        Mono<Void> next;
        if (isNew) {
            next = Mono.from(afterClosed());
        } else {
            next = Mono.empty();
        }
        if (outterThrowable != null) {
            next = next.then(Mono.error(outterThrowable));
        }
        return next;
    }

    private static Mono<Void> finallyBlock(Publisher<Void> resource, @Nullable Throwable outterThrowable, Function<@Nullable Throwable, Mono<Void>> doFinally) {
        return Mono.from(resource)
            .map(ignore -> Optional.<Throwable>empty())
            .onErrorResume(throwable -> Mono.just(Optional.of(throwable)))
            .switchIfEmpty(Mono.just(Optional.empty()))
            .flatMap(optionalThrowable -> {
                Throwable throwable = optionalThrowable.orElse(null);
                if (throwable != null) {
                    if (outterThrowable != null) {
                        throwable.addSuppressed(outterThrowable);
                    }
                    return doFinally.apply(throwable).then(Mono.error(throwable));
                } else {
                    return doFinally.apply(null);
                }
            });
    }

    private Publisher<Void> onComplete() {
        return forEachSynchronizations(ReactiveConnectionSynchronization::onComplete);
    }

    private Publisher<Void> onError(Throwable throwable) {
        return forEachSynchronizations(reactiveConnectionSynchronization -> reactiveConnectionSynchronization.onError(throwable));
    }

    private Publisher<Void> onCancel() {
        return forEachSynchronizations(ReactiveConnectionSynchronization::onCancel);
    }

    private Publisher<Void> beforeClosed() {
        if (isNew) {
            return forEachSynchronizations(ReactiveConnectionSynchronization::onClose);
        }
        return Mono.empty();
    }

    private Publisher<Void> afterClosed() {
        if (isNew) {
            return forEachSynchronizations(ReactiveConnectionSynchronization::afterClose);
        }
        return Mono.empty();
    }
}

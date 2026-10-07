/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.order.Ordered;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.support.DefaultConnectionStatus;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class DefaultReactiveConnectionStatusTest {

    private static final int[] ORDERS = {10, 1, 5};

    @Test
    void reactiveSynchronizationsRunInTheOrderOfASynchronousStatus() {
        var reactiveEvents = new ArrayList<String>();
        var reactiveStatus = newReactiveStatus();
        var syncEvents = new ArrayList<String>();
        var syncStatus = newSyncStatus();
        for (int order : ORDERS) {
            reactiveStatus.registerReactiveSynchronization(new OrderedReactiveSynchronization(order, reactiveEvents));
            syncStatus.registerSynchronization(new OrderedSynchronization(order, syncEvents));
        }

        Mono.from(reactiveStatus.onComplete(Mono::empty)).block();
        syncStatus.complete();

        assertIterableEquals(List.of("10", "5", "1"), syncEvents);
        assertIterableEquals(syncEvents, reactiveEvents);
    }

    @Test
    void synchronousSynchronizationsKeepTheirOrderOnAReactiveStatus() {
        var reactiveEvents = new ArrayList<String>();
        var reactiveStatus = newReactiveStatus();
        var syncEvents = new ArrayList<String>();
        var syncStatus = newSyncStatus();
        for (int order : ORDERS) {
            reactiveStatus.registerSynchronization(new OrderedSynchronization(order, reactiveEvents));
            syncStatus.registerSynchronization(new OrderedSynchronization(order, syncEvents));
        }

        Mono.from(reactiveStatus.onComplete(Mono::empty)).block();
        syncStatus.complete();

        assertIterableEquals(List.of("10", "5", "1"), syncEvents);
        assertIterableEquals(syncEvents, reactiveEvents);
    }

    private static DefaultReactiveConnectionStatus<String> newReactiveStatus() {
        return new DefaultReactiveConnectionStatus<>("connection", ConnectionDefinition.DEFAULT, null, true);
    }

    private static DefaultConnectionStatus<String> newSyncStatus() {
        return new DefaultConnectionStatus<>("connection", ConnectionDefinition.DEFAULT, true, null);
    }

    private record OrderedSynchronization(int order, List<String> events) implements ConnectionSynchronization {

        @Override
        public void executionComplete() {
            events.add(String.valueOf(order));
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    private record OrderedReactiveSynchronization(int order,
                                                  List<String> events) implements ReactiveConnectionSynchronization, Ordered {

        @Override
        public Publisher<Void> onComplete() {
            return Mono.fromRunnable(() -> events.add(String.valueOf(order)));
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}

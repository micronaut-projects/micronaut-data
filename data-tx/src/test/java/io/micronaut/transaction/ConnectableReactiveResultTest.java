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
package io.micronaut.transaction;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.connection.annotation.Connectable;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verifies that {@link Connectable} adapts the result of a reactive method to its return type.
 */
class ConnectableReactiveResultTest {

    private static final String SPEC_NAME = "ConnectableReactiveResultTest";

    @Test
    void reactiveResultIsConvertedToTheReturnType() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            OpLogger opLogger = applicationContext.getBean(OpLogger.class);
            ConnectableService service = applicationContext.getBean(ConnectableService.class);

            CustomPublisher<String> single = service.single();
            Assertions.assertEquals("CONNECTION_1", Mono.from(single).block());

            CustomPublisher<String> multiple = service.multiple();
            Assertions.assertEquals(List.of("CONNECTION_2", "CONNECTION_2"), Flux.from(multiple).collectList().block());

            Assertions.assertEquals(List.of(
                "OPEN CONNECTION_1",
                "CLOSE CONNECTION_1",
                "OPEN CONNECTION_2",
                "CLOSE CONNECTION_2"
            ), opLogger.getLogs());
        }
    }

    /**
     * A reactive type that isn't a Reactor type.
     *
     * @param publisher The publisher
     * @param <T>       The element type
     */
    public record CustomPublisher<T>(Publisher<T> publisher) implements Publisher<T> {

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            publisher.subscribe(subscriber);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class CustomPublisherConverter implements TypeConverter<Publisher, CustomPublisher> {

        @Override
        public Optional<CustomPublisher> convert(Publisher object, Class<CustomPublisher> targetType, ConversionContext context) {
            return Optional.of(new CustomPublisher<>(object));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class ConnectableService {

        private final ReactiveConnManager connManager;

        ConnectableService(ReactiveConnManager connManager) {
            this.connManager = connManager;
        }

        @Connectable
        CustomPublisher<String> single() {
            return new CustomPublisher<>(Mono.deferContextual(contextView ->
                Mono.just(connManager.findConnectionStatus(contextView).orElseThrow().getConnection())
            ));
        }

        @Connectable
        CustomPublisher<String> multiple() {
            return new CustomPublisher<>(Flux.deferContextual(contextView -> {
                String connection = connManager.findConnectionStatus(contextView).orElseThrow().getConnection();
                return Flux.just(connection, connection);
            }));
        }
    }
}

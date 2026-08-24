package io.micronaut.data.connection.support;

import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class AbstractConnectionOperationsTest {

    @Test
    void customizersFireInOrderAndWrapCallback() {
        var operations = new StubConnectionOperations();
        var events = new ArrayList<String>();
        operations.addConnectionCustomizer(new TaggingCustomizer<>("A", events, 10));
        operations.addConnectionCustomizer(new TaggingCustomizer<>("B", events, 20));

        operations.execute(ConnectionDefinition.DEFAULT, _ -> {
            events.add("callback");
            return "result";
        });

        assertIterableEquals(List.of("B-before", "A-before", "callback", "A-after", "B-after"), events);
    }

    private static final class StubConnectionOperations extends AbstractConnectionOperations<Object> {
        @Override
        protected Object openConnection(ConnectionDefinition definition) {
            return new Object();
        }

        @Override
        protected void setupConnection(ConnectionStatus<Object> connectionStatus) {
        }

        @Override
        protected void closeConnection(ConnectionStatus<Object> connectionStatus) {
        }
    }

    private record TaggingCustomizer<C>(String tag, List<String> events, int order)
        implements ConnectionCustomizer<C> {

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public <R> Function<ConnectionStatus<C>, R> intercept(Function<ConnectionStatus<C>, R> operation) {
            return status -> {
                events.add(tag + "-before");
                R result = operation.apply(status);
                events.add(tag + "-after");
                return result;
            };
        }
    }
}

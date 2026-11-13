package io.micronaut.data.connection.support;

import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void reusedConnectionIsSetUpAgainWithTheDefinitionOfTheOuterScope() {
        var events = new ArrayList<String>();
        var operations = new RecordingConnectionOperations(events);

        operations.execute(ConnectionDefinition.READ_ONLY, outer ->
            operations.execute(ConnectionDefinition.DEFAULT, inner -> {
                assertFalse(inner.isNew());
                return null;
            })
        );

        assertEquals(List.of("open", "setup new=true readOnly=true", "setup new=false readOnly=true", "close"), events);
    }

    @Test
    void synchronizationFailureIsThrownAfterTheRemainingCallbacksAndTheClose() {
        var events = new ArrayList<String>();
        var operations = new RecordingConnectionOperations(events);
        var failure = new IllegalStateException("callback");

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            operations.execute(ConnectionDefinition.DEFAULT, status -> {
                status.registerSynchronization(new RecordingSynchronization("first", events));
                status.registerSynchronization(new ConnectionSynchronization() {
                    @Override
                    public void executionComplete() {
                        events.add("failing executionComplete");
                        throw failure;
                    }
                });
                status.registerSynchronization(new RecordingSynchronization("last", events));
                return "value";
            })
        );

        assertSame(failure, e);
        assertEquals(List.of(
            "open", "setup new=true readOnly=null",
            "last executionComplete", "failing executionComplete", "first executionComplete",
            "last beforeClosed", "first beforeClosed",
            "close",
            "last afterClosed", "first afterClosed"), events);
    }

    @Test
    void synchronizationFailureReplacesTheFailureOfTheWork() {
        var operations = new RecordingConnectionOperations(new ArrayList<>());
        var failure = new IllegalStateException("callback");

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            operations.execute(ConnectionDefinition.DEFAULT, status -> {
                status.registerSynchronization(new ConnectionSynchronization() {
                    @Override
                    public void executionComplete() {
                        throw failure;
                    }
                });
                throw new IllegalArgumentException("work");
            })
        );

        assertSame(failure, e);
        assertEquals(0, e.getSuppressed().length);
    }

    @Test
    void synchronizationOfAReusedConnectionRunsWhenTheNestedScopeCompletes() {
        var events = new ArrayList<String>();
        var operations = new RecordingConnectionOperations(events);

        operations.execute(ConnectionDefinition.DEFAULT, outer -> {
            operations.execute(ConnectionDefinition.DEFAULT, inner -> {
                inner.registerSynchronization(new RecordingSynchronization("inner", events));
                return null;
            });
            events.add("nested scope completed");
            return null;
        });

        // The connection isn't closed by the nested scope: only executionComplete is invoked
        assertEquals(List.of(
            "open", "setup new=true readOnly=null", "setup new=false readOnly=null",
            "inner executionComplete",
            "nested scope completed",
            "close"), events);
    }

    private static final class RecordingConnectionOperations extends AbstractConnectionOperations<Object> {

        private final List<String> events;

        private RecordingConnectionOperations(List<String> events) {
            this.events = events;
        }

        @Override
        protected Object openConnection(ConnectionDefinition definition) {
            events.add("open");
            return new Object();
        }

        @Override
        protected void setupConnection(ConnectionStatus<Object> connectionStatus) {
            events.add("setup new=" + connectionStatus.isNew() + " readOnly=" + connectionStatus.getDefinition().isReadOnly().orElse(null));
        }

        @Override
        protected void closeConnection(ConnectionStatus<Object> connectionStatus) {
            events.add("close");
        }
    }

    private record RecordingSynchronization(String name, List<String> events) implements ConnectionSynchronization {

        @Override
        public void executionComplete() {
            events.add(name + " executionComplete");
        }

        @Override
        public void beforeClosed() {
            events.add(name + " beforeClosed");
        }

        @Override
        public void afterClosed() {
            events.add(name + " afterClosed");
        }
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

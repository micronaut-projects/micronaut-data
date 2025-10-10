package io.micronaut.coherence.data.util;

import io.micronaut.coherence.data.model.Book;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.event.listeners.PrePersistEventListener;
import io.micronaut.data.event.listeners.PreRemoveEventListener;
import io.micronaut.data.event.listeners.PreUpdateEventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Factory
@SuppressWarnings("checkstyle:DesignForExtension")
public class BookEvictionListeners {

    @Inject
    EventRecorder<Book> eventRecorder;

    @Singleton
    @Requires(env = "evict-persist")
    PrePersistEventListener<Book> beforePersist() {
        return (book) -> {
            eventRecorder.record(EventType.PRE_PERSIST, book);
            return false;
        };
    }

    @Singleton
    @Requires(env = "evict-update")
    PreUpdateEventListener<Book> beforeUpdate() {
        return (book) -> {
            eventRecorder.record(EventType.PRE_UPDATE, book);
            return false;
        };
    }

    @Singleton
    @Requires(env = "evict-remove")
    PreRemoveEventListener<Book> beforeRemove() {
        return (book) -> {
            eventRecorder.record(EventType.PRE_REMOVE, book);
            return false;
        };
    }
}

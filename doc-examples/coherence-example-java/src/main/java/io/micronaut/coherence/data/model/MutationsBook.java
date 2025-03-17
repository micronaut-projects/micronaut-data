package io.micronaut.coherence.data.model;

import io.micronaut.coherence.data.util.EventRecorder;
import io.micronaut.coherence.data.util.EventType;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.event.PrePersist;
import jakarta.inject.Inject;

import java.util.Calendar;

@Introspected
public final class MutationsBook extends Book {

    @Inject
    transient EventRecorder<Book> eventRecorder;

    public MutationsBook(final String title, final int pages, final Author author, final Calendar published) {
        super(title, pages, author, published);
    }

    public MutationsBook(final Book copy) {
        super(copy);
    }

    @PrePersist
    void prePersist() {
        eventRecorder.record(EventType.PRE_PERSIST, new Book(this)); // copy book for event inspection prior to mutation
        setPages(1000);
    }
}

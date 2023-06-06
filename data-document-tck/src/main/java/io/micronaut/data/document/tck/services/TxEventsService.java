package io.micronaut.data.document.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import jakarta.inject.Singleton;

import javax.transaction.Transactional;
import java.io.IOException;

@Singleton
public class TxEventsService extends AbstractBookService {

    private final ApplicationEventPublisher<NewBookEvent> eventPublisher;

    NewBookEvent lastEvent;

    public TxEventsService(ApplicationEventPublisher<NewBookEvent> eventPublisher, ApplicationContext beanContext) {
        super(beanContext);
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void insertWithTransaction() {
        String bookName = "The Stand";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
    }

    @Transactional
    public void insertAndRollback() {
        String bookName = "Bad things happened";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
        throw new RuntimeException("Bad things happened");
    }

    @Transactional
    public void insertAndRollbackChecked() throws Exception {
        String bookName = "The Shining";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
        throw new Exception("Bad things happened");
    }

    @Transactional(dontRollbackOn = IOException.class)
    public void insertAndRollbackDontRollbackOn() throws IOException {
        String bookName = "The Shining";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
        throw new IOException("Bad things happened");
    }

    @TransactionalEventListener
    protected void afterCommit(NewBookEvent event) {
        lastEvent = event;
    }

    public void cleanLastEvent() {
        lastEvent = null;
    }

    public NewBookEvent getLastEvent() {
        return lastEvent;
    }

    public record NewBookEvent(String title) {
    }
}

package io.micronaut.data.tck.services;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import io.micronaut.transaction.support.TransactionSynchronization;
import jakarta.inject.Singleton;

import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Requires(property = AbstractBookService.BOOK_REPOSITORY_CLASS_PROPERTY)
@Singleton
public class TxEventsService extends AbstractBookService {

    private final ApplicationEventPublisher<NewBookEvent> eventPublisher;
    private final TransactionOperations<?> transactionOperations;

    NewBookEvent lastEvent;
    List<String> events = new ArrayList<>();

    public TxEventsService(ApplicationEventPublisher<NewBookEvent> eventPublisher,
                           ApplicationContext beanContext,
                           TransactionOperations<?> transactionOperations) {
        super(beanContext);
        this.eventPublisher = eventPublisher;
        this.transactionOperations = transactionOperations;
    }

    @Transactional
    public void insertWithTransaction() {
        registerMainSync();
        String bookName = "The Stand";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void insertWithRequiresNewTransaction() {
        registerMainSync();
        String bookName = "The Stand";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
    }

    @Transactional
    public void insertWithOuterTransaction() {
        registerOuterSync();
        events.add("ENTER INNER");
        insertWithTransaction();
        events.add("EXIT INNER");
        if (lastEvent != null) {
            throw new IllegalStateException("Event should be triggered after TX commit");
        }
    }

    @Transactional
    public void insertWithOuterNewTransaction() {
        registerOuterSync();
        events.add("ENTER INNER");
        insertWithRequiresNewTransaction();
        events.add("EXIT INNER");
        if (lastEvent == null) {
            throw new IllegalStateException("Event should have be triggered after TX commit");
        }
    }

    @Transactional
    public void insertAndRollback() {
        registerMainSync();
        String bookName = "Bad things happened";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
        throw new RuntimeException("Bad things happened");
    }


    @Transactional
    public void insertAndRollbackWithOuterTransaction() {
        registerOuterSync();
        events.add("ENTER INNER");
        insertAndRollback();
        events.add("EXIT INNER");
    }

    @Transactional
    public void insertAndRollbackChecked() throws Exception {
        registerMainSync();
        String bookName = "The Shining";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
        throw new Exception("Bad things happened");
    }

    @Transactional
    public void insertAndRollbackCheckedWithOuterTransaction() throws Exception {
        registerOuterSync();
        events.add("ENTER INNER");
        insertAndRollbackChecked();
        events.add("EXIT INNER");
    }

    @Transactional(dontRollbackOn = IOException.class)
    public void insertAndRollbackDontRollbackOn() throws IOException {
        registerMainSync();
        String bookName = "The Shining";
        bookRepository.save(newBook(bookName));
        eventPublisher.publishEvent(new NewBookEvent(bookName));
        throw new IOException("Bad things happened");
    }

    @TransactionalEventListener
    protected void afterCommit(NewBookEvent event) {
        lastEvent = event;
    }

    private void registerMainSync() {
        transactionOperations.findTransactionStatus().orElseThrow().registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                events.add("BEFORE COMMIT: " + readOnly);
            }

            @Override
            public void beforeCompletion() {
                events.add("BEFORE COMPLETION");
            }

            @Override
            public void afterCommit() {
                events.add("AFTER COMMIT");
            }

            @Override
            public void afterCompletion(Status status) {
                events.add("AFTER COMPLETION: " + status);
            }
        });
    }

    private void registerOuterSync() {
        transactionOperations.findTransactionStatus().orElseThrow().registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                events.add("OUTER BEFORE COMMIT: " + readOnly);
            }

            @Override
            public void beforeCompletion() {
                events.add("OUTER BEFORE COMPLETION");
            }

            @Override
            public void afterCommit() {
                events.add("OUTER AFTER COMMIT");
            }

            @Override
            public void afterCompletion(Status status) {
                events.add("OUTER AFTER COMPLETION: " + status);
            }
        });
    }

    @Override
    public void cleanup() {
        lastEvent = null;
        events = new ArrayList<>();
        super.cleanup();
    }

    public NewBookEvent getLastEvent() {
        return lastEvent;
    }

    public List<String> getEvents() {
        return events;
    }

    public record NewBookEvent(String title) {
    }
}

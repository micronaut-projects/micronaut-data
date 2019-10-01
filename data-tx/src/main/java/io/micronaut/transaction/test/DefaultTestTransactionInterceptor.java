package io.micronaut.transaction.test;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.transaction.TestTransactionInterceptor;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adds support for {@link io.micronaut.test.annotation.MicronautTest#transactional()} handling.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachBean(SynchronousTransactionManager.class)
@Requires(classes = TestTransactionInterceptor.class)
public class DefaultTestTransactionInterceptor implements TestTransactionInterceptor {
    private final SynchronousTransactionManager transactionManager;
    private TransactionStatus<?> tx;
    private final AtomicInteger counter = new AtomicInteger();

    /**
     * @param transactionManager The transaction manager.
     */
    protected DefaultTestTransactionInterceptor(SynchronousTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public void begin() {
        if (counter.getAndIncrement() == 0) {
            tx = transactionManager.getTransaction(TransactionDefinition.DEFAULT);
        }
    }

    @Override
    public void commit() {
        if (counter.decrementAndGet() == 0) {
            transactionManager.commit(tx);
        }
    }

    @Override
    public void rollback() {
        if (counter.decrementAndGet() == 0) {
            transactionManager.rollback(tx);
        }
    }
}

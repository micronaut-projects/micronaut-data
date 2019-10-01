package io.micronaut.data.hibernate.runtime.spring;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionException;
import org.hibernate.SessionFactory;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import java.util.function.Function;

/**
 * Adds Spring Transaction management capability to Micronaut Data.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Requires(classes = HibernateTransactionManager.class)
@EachBean(HibernateTransactionManager.class)
@Internal
public class SpringHibernateTransactionOperations implements TransactionOperations<EntityManager> {

    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final SessionFactory sessionFactory;

    /**
     * Default constructor.
     * @param hibernateTransactionManager The hibernate transaction manager.
     */
    protected SpringHibernateTransactionOperations(HibernateTransactionManager hibernateTransactionManager) {
        this.sessionFactory = hibernateTransactionManager.getSessionFactory();
        this.writeTransactionTemplate = new TransactionTemplate(hibernateTransactionManager);
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(hibernateTransactionManager, transactionDefinition);
    }

    @Override
    public <R> R executeRead(@NonNull Function<TransactionStatus<EntityManager>, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        return readTransactionTemplate.execute(status ->
                callback.apply(new JpaTransactionStatus(status))
        );
    }

    @Override
    public <R> R executeWrite(@NonNull Function<TransactionStatus<EntityManager>, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        return writeTransactionTemplate.execute(status ->
                callback.apply(new JpaTransactionStatus(status))
        );
    }

    @NonNull
    @Override
    public EntityManager getConnection() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Internal transaction status.
     */
    private final class JpaTransactionStatus implements TransactionStatus<EntityManager> {

        private final org.springframework.transaction.TransactionStatus springStatus;

        JpaTransactionStatus(org.springframework.transaction.TransactionStatus springStatus) {
            this.springStatus = springStatus;
        }

        @Override
        public boolean isNewTransaction() {
            return springStatus.isNewTransaction();
        }

        @Override
        public void setRollbackOnly() {
            springStatus.setRollbackOnly();
        }

        @Override
        public boolean isRollbackOnly() {
            return springStatus.isRollbackOnly();
        }

        @Override
        public boolean isCompleted() {
            return springStatus.isCompleted();
        }

        @Override
        public boolean hasSavepoint() {
            return springStatus.hasSavepoint();
        }

        @Override
        public void flush() {
            springStatus.flush();
        }

        @NonNull
        @Override
        public Object getTransaction() {
            return springStatus;
        }

        @NonNull
        @Override
        public EntityManager getConnection() {
            return sessionFactory.getCurrentSession();
        }

        @Override
        public Object createSavepoint() throws TransactionException {
            return springStatus.createSavepoint();
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) throws TransactionException {
            springStatus.rollbackToSavepoint(savepoint);
        }

        @Override
        public void releaseSavepoint(Object savepoint) throws TransactionException {
            springStatus.releaseSavepoint(savepoint);
        }
    }
}


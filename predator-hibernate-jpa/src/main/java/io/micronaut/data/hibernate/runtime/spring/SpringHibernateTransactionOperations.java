package io.micronaut.data.hibernate.runtime.spring;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.transaction.TransactionCallback;
import io.micronaut.data.transaction.TransactionOperations;
import org.hibernate.SessionFactory;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;

/**
 * Adds Spring Transaction management capability to Predator.
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

    @Nullable
    @Override
    public <R> R executeWrite(@NonNull TransactionCallback<EntityManager, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        return writeTransactionTemplate.execute(status ->
                callback.apply(new JpaTransactionStatus(status))
        );
    }

    @Nullable
    @Override
    public <R> R executeRead(@NonNull TransactionCallback<EntityManager, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        return readTransactionTemplate.execute(status ->
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
    private final class JpaTransactionStatus implements io.micronaut.data.transaction.TransactionStatus<EntityManager> {

        private final org.springframework.transaction.TransactionStatus springStatus;

        JpaTransactionStatus(org.springframework.transaction.TransactionStatus springStatus) {
            this.springStatus = springStatus;
        }

        @NonNull
        @Override
        public EntityManager getResource() {
            return sessionFactory.getCurrentSession();
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
    }
}


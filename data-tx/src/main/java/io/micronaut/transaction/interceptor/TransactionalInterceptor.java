package io.micronaut.transaction.interceptor;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.interceptor.annotation.TransactionalAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link TransactionalAdvice}. Forked from the reflection based code in Spring.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Stéphane Nicoll
 * @author Sam Brannen
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = SynchronousTransactionManager.class)
public class TransactionalInterceptor implements MethodInterceptor<Object, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionalInterceptor.class);
    /**
     * Holder to support the {@code currentTransactionStatus()} method,
     * and to support communication between different cooperating advices
     * (e.g. before and after advice) if the aspect involves more than a
     * single method (as will be the case for around advice).
     */
    @SuppressWarnings("unchecked")
    private static final ThreadLocal<TransactionInfo> TRANSACTION_INFO_HOLDER =
            new ThreadLocal() {
                @Override
                public String toString() {
                    return "Current aspect-driven transaction";
                }
            };
    private final Map<ExecutableMethod, TransactionInvocation> transactionInvocationMap = new ConcurrentHashMap<>(30);

    @NonNull
    private final BeanLocator beanLocator;

    /**
     * Default constructor.
     *
     * @param beanLocator The bean locator.
     */
    public TransactionalInterceptor(@NonNull BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final TransactionInvocation transactionInvocation = transactionInvocationMap
                .computeIfAbsent(context.getExecutableMethod(), executableMethod -> {
            final String qualifier = executableMethod.stringValue(TransactionalAdvice.class).orElse(null);
            SynchronousTransactionManager transactionManager =
                    beanLocator.getBean(SynchronousTransactionManager.class, qualifier != null ? Qualifiers.byName(qualifier) : null);
            final TransactionAttribute transactionAttribute = resolveTransactionDefinition(executableMethod);

            return new TransactionInvocation(transactionManager, transactionAttribute);
        });
        final TransactionAttribute definition = transactionInvocation.definition;
        final SynchronousTransactionManager transactionManager = transactionInvocation.transactionManager;
        final TransactionInfo transactionInfo = createTransactionIfNecessary(
                transactionManager,
                definition,
                definition.getName());
        Object retVal;
        try {
            retVal = context.proceed();
        } catch (Throwable ex) {
            completeTransactionAfterThrowing(transactionInfo, ex);
            throw ex;
        } finally {
            cleanupTransactionInfo(transactionInfo);
        }
        commitTransactionAfterReturning(transactionInfo);
        return retVal;
    }

    /**
     * Create a transaction if necessary based on the given TransactionAttribute.
     * <p>Allows callers to perform custom TransactionAttribute lookups through
     * the TransactionAttributeSource.
     * @param tm The transaction manager
     * @param txAttr the TransactionAttribute (may be {@code null})
     * @param joinpointIdentification the fully qualified method name
     * (used for monitoring and logging purposes)
     * @return a TransactionInfo object, whether or not a transaction was created.
     * The {@code hasTransaction()} method on TransactionInfo can be used to
     * tell if there was a transaction created.
     */
    @SuppressWarnings("serial")
    protected TransactionInfo createTransactionIfNecessary(@NonNull SynchronousTransactionManager tm,
                                                           @NonNull TransactionAttribute txAttr,
                                                           final String joinpointIdentification) {


        TransactionStatus status;
        status = tm.getTransaction(txAttr);
        return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
    }

    /**
     * Prepare a TransactionInfo for the given attribute and status object.
     * @param tm The transaction manager
     * @param txAttr the TransactionAttribute (may be {@code null})
     * @param joinpointIdentification the fully qualified method name
     * (used for monitoring and logging purposes)
     * @param status the TransactionStatus for the current transaction
     * @return the prepared TransactionInfo object
     */
    protected TransactionInfo prepareTransactionInfo(@NonNull SynchronousTransactionManager tm,
                                                     @NonNull TransactionAttribute txAttr,
                                                     String joinpointIdentification,
                                                     @NonNull TransactionStatus status) {

        TransactionInfo txInfo = new TransactionInfo(tm, txAttr, joinpointIdentification);
        // We need a transaction for this method...
        if (LOG.isTraceEnabled()) {
            LOG.trace("Getting transaction for [" + txInfo.getJoinpointIdentification() + "]");
        }
        // The transaction manager will flag an error if an incompatible tx already exists.
        txInfo.newTransactionStatus(status);

        // We always bind the TransactionInfo to the thread, even if we didn't create
        // a new transaction here. This guarantees that the TransactionInfo stack
        // will be managed correctly even if no transaction was created by this aspect.
        txInfo.bindToThread();
        return txInfo;
    }

    /**
     * Execute after successful completion of call, but not after an exception was handled.
     * Do nothing if we didn't create a transaction.
     * @param txInfo information about the current transaction
     */
    protected void commitTransactionAfterReturning(@NonNull TransactionInfo txInfo) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() + "]");
        }
        txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
    }

    /**
     * Handle a throwable, completing the transaction.
     * We may commit or roll back, depending on the configuration.
     * @param txInfo information about the current transaction
     * @param ex throwable encountered
     */
    protected void completeTransactionAfterThrowing(@NonNull TransactionInfo txInfo, Throwable ex) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Completing transaction for [" + txInfo.getJoinpointIdentification() +
                    "] after exception: " + ex);
        }
        if (txInfo.transactionAttribute.rollbackOn(ex)) {
            try {
                txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());
            } catch (TransactionSystemException ex2) {
                LOG.error("Application exception overridden by rollback exception", ex);
                ex2.initApplicationException(ex);
                throw ex2;
            } catch (RuntimeException | Error ex2) {
                LOG.error("Application exception overridden by rollback exception", ex);
                throw ex2;
            }
        } else {
            // We don't roll back on this exception.
            // Will still roll back if TransactionStatus.isRollbackOnly() is true.
            try {
                txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());
            } catch (TransactionSystemException ex2) {
                LOG.error("Application exception overridden by commit exception", ex);
                ex2.initApplicationException(ex);
                throw ex2;
            } catch (RuntimeException | Error ex2) {
                LOG.error("Application exception overridden by commit exception", ex);
                throw ex2;
            }
        }
    }

    /**
     * Reset the TransactionInfo ThreadLocal.
     * <p>Call this in all cases: exception or normal return!
     * @param txInfo information about the current transaction (may be {@code null})
     */
    protected void cleanupTransactionInfo(@Nullable TransactionInfo txInfo) {
        if (txInfo != null) {
            txInfo.restoreThreadLocalStatus();
        }
    }

    /**
     * @param executableMethod The method
     * @return The {@link TransactionAttribute}
     */
    protected TransactionAttribute resolveTransactionDefinition(
            ExecutableMethod<Object, Object> executableMethod) {
        AnnotationValue<TransactionalAdvice> annotation = executableMethod.getAnnotation(TransactionalAdvice.class);

        if (annotation == null) {
            throw new IllegalStateException("No declared @Transactional annotation present");
        }

        DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();
        attribute.setName(executableMethod.getDeclaringType().getSimpleName() + "." + executableMethod.getMethodName());
        attribute.setReadOnly(annotation.isTrue("readOnly"));
        annotation.intValue("timeout").ifPresent(value -> attribute.setTimeout(Duration.ofSeconds(value)));
        final Class[] noRollbackFors = annotation.classValues("noRollbackFor");
        //noinspection unchecked
        attribute.setNoRollbackFor(noRollbackFors);
        annotation.enumValue("propagation", TransactionDefinition.Propagation.class)
                .ifPresent(attribute::setPropagationBehavior);
        annotation.enumValue("isolation", TransactionDefinition.Isolation.class)
                .ifPresent(attribute::setIsolationLevel);
        return attribute;
    }

    /**
     * Cached invocation associating a method with a definition a transaction manager.
     */
    private final class TransactionInvocation {
        final SynchronousTransactionManager transactionManager;
        final TransactionAttribute definition;

        TransactionInvocation(SynchronousTransactionManager transactionManager, TransactionAttribute definition) {
            this.transactionManager = transactionManager;
            this.definition = definition;
        }
    }

    /**
     * Opaque object used to hold transaction information. Subclasses
     * must pass it back to methods on this class, but not see its internals.
     */
    protected final class TransactionInfo {

        private final SynchronousTransactionManager transactionManager;
        private final TransactionAttribute transactionAttribute;

        private final String joinpointIdentification;

        @NonNull
        private TransactionStatus transactionStatus;

        @NonNull
        private TransactionInfo oldTransactionInfo;

        /**
         * Constructs a new transaction info.
         * @param transactionManager The transaction manager
         * @param transactionAttribute The transaction attribute
         * @param joinpointIdentification The joint point identification
         */
        protected TransactionInfo(@NonNull SynchronousTransactionManager transactionManager,
                               @NonNull TransactionAttribute transactionAttribute,
                               @NonNull String joinpointIdentification) {

            this.transactionManager = transactionManager;
            this.transactionAttribute = transactionAttribute;
            this.joinpointIdentification = joinpointIdentification;
        }

        /**
         * @return The transaction manager
         */
        @NonNull
        public SynchronousTransactionManager getTransactionManager() {
            return this.transactionManager;
        }

        /**
         * @return Return a String representation of this joinpoint (usually a Method call)
         * for use in logging.
         */
        @NonNull
        public String getJoinpointIdentification() {
            return this.joinpointIdentification;
        }

        /**
         * Create a new status.
         * @param status The status.
         */
        public void newTransactionStatus(@NonNull TransactionStatus status) {
            this.transactionStatus = status;
        }

        /**
         * @return The underlying status.
         */
        @NonNull
        public TransactionStatus getTransactionStatus() {
            return this.transactionStatus;
        }

        /**
         * @return Return whether a transaction was created by this aspect,
         * or whether we just have a placeholder to keep ThreadLocal stack integrity.
         */
        public boolean hasTransaction() {
            return true;
        }

        private void bindToThread() {
            // Expose current TransactionStatus, preserving any existing TransactionStatus
            // for restoration after this transaction is complete.
            this.oldTransactionInfo = TRANSACTION_INFO_HOLDER.get();
            TRANSACTION_INFO_HOLDER.set(this);
        }

        private void restoreThreadLocalStatus() {
            // Use stack to restore old transaction TransactionInfo.
            // Will be null if none was set.
            TRANSACTION_INFO_HOLDER.set(this.oldTransactionInfo);
        }

        @Override
        public String toString() {
            return this.transactionAttribute.toString();
        }
    }
}

/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.transaction.interceptor;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.kotlin.KotlinInterceptedMethod;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.TransactionalAdvice;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
public class TransactionalInterceptor implements MethodInterceptor<Object, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionalInterceptor.class);
    /**
     * Holder to support the {@code currentTransactionStatus()} method,
     * and to support communication between different cooperating advices
     * (e.g. before and after advice) if the aspect involves more than a
     * single method (as will be the case for around advice).
     */
    private static final ThreadLocal<TransactionInfo> TRANSACTION_INFO_HOLDER =
            new ThreadLocal<TransactionInfo>() {
                @Override
                public String toString() {
                    return "Current aspect-driven transaction";
                }
            };
    private final Map<ExecutableMethod, TransactionInvocation> transactionInvocationMap = new ConcurrentHashMap<>(30);

    @NonNull
    private final BeanLocator beanLocator;
    private final CoroutineTxHelper coroutineTxHelper;

    /**
     * Default constructor.
     *
     * @param beanLocator The bean locator.
     */
    public TransactionalInterceptor(@NonNull BeanLocator beanLocator) {
        this(beanLocator, null);
    }

    /**
     * Default constructor.
     *
     * @param beanLocator       The bean locator.
     * @param coroutineTxHelper The coroutine helper
     */
    @Inject
    public TransactionalInterceptor(@NonNull BeanLocator beanLocator, @Nullable CoroutineTxHelper coroutineTxHelper) {
        this.beanLocator = beanLocator;
        this.coroutineTxHelper = coroutineTxHelper;
    }

    @Override
    public int getOrder() {
        return InterceptPhase.TRANSACTION.getPosition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context);
        boolean isKotlinSuspended = interceptedMethod instanceof KotlinInterceptedMethod;
        try {
            boolean isReactive = interceptedMethod.resultType() == InterceptedMethod.ResultType.PUBLISHER;
            boolean isAsync = interceptedMethod.resultType() == InterceptedMethod.ResultType.COMPLETION_STAGE;

            final TransactionInvocation<?> transactionInvocation = transactionInvocationMap
                    .computeIfAbsent(context.getExecutableMethod(), executableMethod -> {
                        final String qualifier = executableMethod.stringValue(TransactionalAdvice.class).orElse(null);

                        ReactiveTransactionOperations<?> reactiveTransactionOperations
                                = beanLocator.findBean(ReactiveTransactionOperations.class, qualifier != null ? Qualifiers.byName(qualifier) : null).orElse(null);

                        if ((isReactive || isAsync) && !(isKotlinSuspended && reactiveTransactionOperations == null)) {
                            if (isReactive && reactiveTransactionOperations == null) {
                                throw new ConfigurationException("No reactive transaction management has been configured. Ensure you have correctly configured a reactive capable transaction manager");
                            } else {
                                final TransactionAttribute transactionAttribute = resolveTransactionDefinition(executableMethod);
                                return new TransactionInvocation(null, reactiveTransactionOperations, transactionAttribute);
                            }
                        } else {

                            SynchronousTransactionManager<?> transactionManager =
                                    beanLocator.getBean(SynchronousTransactionManager.class, qualifier != null ? Qualifiers.byName(qualifier) : null);
                            final TransactionAttribute transactionAttribute = resolveTransactionDefinition(executableMethod);

                            return new TransactionInvocation<>(transactionManager, null, transactionAttribute);
                        }
                    });

            final TransactionAttribute definition = transactionInvocation.definition;
            switch (interceptedMethod.resultType()) {
                case PUBLISHER:
                    return interceptedMethod.handleResult(
                            transactionInvocation.reactiveTransactionOperations.withTransaction(definition, (status) -> {
                                context.setAttribute(ReactiveTransactionStatus.STATUS, status);
                                context.setAttribute(ReactiveTransactionStatus.ATTRIBUTE, definition);
                                return Publishers.convertPublisher(context.proceed(), Publisher.class);
                            })
                    );
                case COMPLETION_STAGE:
                    if (transactionInvocation.reactiveTransactionOperations != null) {
                        return interceptedMethod.handleResult(interceptedMethod.interceptResult());
                    } else {
                        if (isKotlinSuspended) {
                            final SynchronousTransactionManager<?> transactionManager = transactionInvocation.transactionManager;
                            final TransactionInfo transactionInfo = createTransactionIfNecessary(
                                    transactionManager,
                                    definition,
                                    context.getExecutableMethod()
                            );
                            KotlinInterceptedMethod kotlinInterceptedMethod = (KotlinInterceptedMethod) interceptedMethod;
                            if (coroutineTxHelper != null) {
                                coroutineTxHelper.setupCoroutineContext(kotlinInterceptedMethod);
                            }
                            CompletionStage<?> result = interceptedMethod.interceptResultAsCompletionStage();
                            CompletableFuture newResult = new CompletableFuture();
                            result.whenComplete((o, throwable) -> {
                                if (throwable == null) {
                                    commitTransactionAfterReturning(transactionInfo);
                                    newResult.complete(o);
                                } else {
                                    try {
                                        completeTransactionAfterThrowing(transactionInfo, throwable);
                                    } catch (Exception e) {
                                        // Ignore rethrow
                                    }
                                    newResult.completeExceptionally(throwable);
                                }
                                cleanupTransactionInfo(transactionInfo);
                            });
                            return interceptedMethod.handleResult(newResult);
                        } else {
                            throw new ConfigurationException("Async return type doesn't support transactional execution.");
                        }
                    }
                case SYNCHRONOUS:
                    final SynchronousTransactionManager<?> transactionManager = transactionInvocation.transactionManager;
                    final TransactionInfo transactionInfo = createTransactionIfNecessary(
                            transactionManager,
                            definition,
                            context.getExecutableMethod()
                    );
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
                default:
                    return interceptedMethod.unsupported();

            }
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    @Nullable
    private static TransactionInfo currentTransactionInfo() throws NoTransactionException {
        return TRANSACTION_INFO_HOLDER.get();
    }

    /**
     * Return the transaction status of the current method invocation.
     * Mainly intended for code that wants to set the current transaction
     * rollback-only but not throw an application exception.
     *
     * @param <T> The connection type
     * @return The current status
     * @throws NoTransactionException if the transaction info cannot be found,
     *                                because the method was invoked outside an AOP invocation context
     */
    public static <T> TransactionStatus<T> currentTransactionStatus() throws NoTransactionException {
        TransactionInfo info = currentTransactionInfo();
        if (info == null) {
            throw new NoTransactionException("No transaction aspect-managed TransactionStatus in scope");
        }
        //noinspection unchecked
        return info.transactionStatus;
    }

    /**
     * Create a transaction if necessary based on the given TransactionAttribute.
     * <p>Allows callers to perform custom TransactionAttribute lookups through
     * the TransactionAttributeSource.
     *
     * @param tm               The transaction manager
     * @param txAttr           the TransactionAttribute (may be {@code null})
     * @param executableMethod the method that is being executed
     *                         (used for monitoring and logging purposes)
     * @return a TransactionInfo object, whether or not a transaction was created.
     * The {@code hasTransaction()} method on TransactionInfo can be used to
     * tell if there was a transaction created.
     */
    @SuppressWarnings("serial")
    protected TransactionInfo createTransactionIfNecessary(@NonNull SynchronousTransactionManager<?> tm,
                                                           @NonNull TransactionAttribute txAttr,
                                                           final ExecutableMethod<Object, Object> executableMethod) {


        TransactionStatus<?> status;
        status = tm.getTransaction(txAttr);
        return prepareTransactionInfo(tm, txAttr, executableMethod, status);
    }

    /**
     * Prepare a TransactionInfo for the given attribute and status object.
     *
     * @param tm               The transaction manager
     * @param txAttr           the TransactionAttribute (may be {@code null})
     * @param executableMethod the fully qualified method name
     *                         (used for monitoring and logging purposes)
     * @param status           the TransactionStatus for the current transaction
     * @return the prepared TransactionInfo object
     */
    protected TransactionInfo prepareTransactionInfo(@NonNull SynchronousTransactionManager tm,
                                                     @NonNull TransactionAttribute txAttr,
                                                     ExecutableMethod<Object, Object> executableMethod,
                                                     @NonNull TransactionStatus status) {

        TransactionInfo txInfo = new TransactionInfo(tm, txAttr, executableMethod);
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
     *
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
     *
     * @param txInfo information about the current transaction
     * @param ex     throwable encountered
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
     *
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
     *
     * @param <C> connection type
     */
    private static final class TransactionInvocation<C> {
        final @Nullable
        SynchronousTransactionManager<C> transactionManager;
        final @Nullable
        ReactiveTransactionOperations<C> reactiveTransactionOperations;
        final TransactionAttribute definition;

        TransactionInvocation(
                SynchronousTransactionManager<C> transactionManager,
                ReactiveTransactionOperations<C> reactiveTransactionOperations,
                TransactionAttribute definition) {
            this.transactionManager = transactionManager;
            this.reactiveTransactionOperations = reactiveTransactionOperations;
            this.definition = definition;
        }

        boolean isReactive() {
            return reactiveTransactionOperations != null;
        }
    }

    /**
     * Opaque object used to hold transaction information. Subclasses
     * must pass it back to methods on this class, but not see its internals.
     *
     * @param <T> connection type
     */
    protected static final class TransactionInfo<T> {

        private final SynchronousTransactionManager<T> transactionManager;
        private final TransactionAttribute transactionAttribute;
        private final ExecutableMethod<Object, Object> executableMethod;
        private TransactionStatus<T> transactionStatus;
        private TransactionInfo<T> oldTransactionInfo;

        /**
         * Constructs a new transaction info.
         *
         * @param transactionManager   The transaction manager
         * @param transactionAttribute The transaction attribute
         * @param executableMethod     The joint point identification
         */
        protected TransactionInfo(@NonNull SynchronousTransactionManager<T> transactionManager,
                                  @NonNull TransactionAttribute transactionAttribute,
                                  @NonNull ExecutableMethod<Object, Object> executableMethod) {

            this.transactionManager = transactionManager;
            this.transactionAttribute = transactionAttribute;
            this.executableMethod = executableMethod;
        }

        /**
         * @return The transaction manager
         */
        @NonNull
        public SynchronousTransactionManager<T> getTransactionManager() {
            return this.transactionManager;
        }

        /**
         * @return Return a String representation of this joinpoint (usually a Method call)
         * for use in logging.
         */
        @NonNull
        public String getJoinpointIdentification() {
            return executableMethod.getDeclaringType().getName() + " . " + this.executableMethod.toString();
        }

        /**
         * Create a new status.
         *
         * @param status The status.
         */
        public void newTransactionStatus(@NonNull TransactionStatus<T> status) {
            this.transactionStatus = status;
        }

        /**
         * @return The underlying status.
         */
        @NonNull
        public TransactionStatus<T> getTransactionStatus() {
            if (transactionStatus == null) {
                throw new IllegalStateException("Transaction status not yet initialized");
            }
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

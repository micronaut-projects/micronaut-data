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
package io.micronaut.transaction.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Central delegate that manages resources and transaction synchronizations per thread.
 * To be used by resource management code but not by typical application code.
 *
 * <p>Supports one resource per key without overwriting, that is, a resource needs
 * to be removed before a new one can be set for the same key.
 * Supports a list of transaction synchronizations if synchronization is active.
 *
 * <p>Resource management code should check for thread-bound resources, e.g. JDBC
 * Connections or Hibernate Sessions, via {@code getResource}. Such code is
 * normally not supposed to bind resources to threads, as this is the responsibility
 * of transaction managers. A further option is to lazily bind on first use if
 * transaction synchronization is active, for performing transactions that span
 * an arbitrary number of resources.
 *
 * <p>Transaction synchronization must be activated and deactivated by a transaction
 * manager via {@link #initSynchronization()} and {@link #clearSynchronization()}.
 * This is automatically supported by {@link AbstractSynchronousTransactionManager},
 * and thus by all standard transaction managers.
 *
 * <p>Resource management code should only register synchronizations when this
 * manager is active, which can be checked via {@link #isSynchronizationActive};
 * it should perform immediate resource cleanup else. If transaction synchronization
 * isn't active, there is either no current transaction, or the transaction manager
 * doesn't support transaction synchronization.
 *
 * <p>Synchronization is for example used to always return the same resources
 * within a JTA transaction, e.g. a JDBC Connection or a Hibernate Session for
 * any given DataSource or SessionFactory, respectively.
 *
 * @author Juergen Hoeller
 * @see #isSynchronizationActive
 * @see #registerSynchronization
 * @see TransactionSynchronization
 * @see AbstractSynchronousTransactionManager#setTransactionSynchronization
 * @since 02.06.2003
 */
public abstract class TransactionSynchronizationManager {

    public static final Object DEFAULT_STATE_KEY = new Object();

    private static final Logger LOG = LoggerFactory.getLogger(TransactionSynchronizationManager.class);

    private static final ThreadLocal<MutableTransactionSynchronizationState> STATE = new ThreadLocal<MutableTransactionSynchronizationState>() {
        @Override
        public String toString() {
            return "The state";
        }
    };

    @NonNull
    private static MutableTransactionSynchronizationState getOrCreateInternalState() {
        MutableTransactionSynchronizationState mutableState = STATE.get();
        if (mutableState == null) {
            mutableState = new MutableTransactionSynchronizationState();
            STATE.set(mutableState);
        }
        return mutableState;
    }

    @NonNull
    private static MutableTransactionSynchronizationState getInternalState() {
        MutableTransactionSynchronizationState mutableState = STATE.get();
        if (mutableState == null) {
            mutableState = new MutableTransactionSynchronizationState();
        }
        return mutableState;
    }

    //-------------------------------------------------------------------------
    // Management of transaction-associated resource handles
    //-------------------------------------------------------------------------

    /**
     * Return all resources that are bound to the current thread.
     * <p>Mainly for debugging purposes. Resource managers should always invoke
     * {@code hasResource} for a specific resource key that they are interested in.
     *
     * @return a Map with resource keys (usually the resource factory) and resource
     * values (usually the active resource object), or an empty Map if there are
     * currently no resources bound
     * @see #hasResource
     */
    public static Map<Object, Object> getResourceMap() {
        return Collections.unmodifiableMap(getInternalState().getResources());
    }

    /**
     * Check if there is a resource for the given key bound to the current thread.
     *
     * @param key the key to check (usually the resource factory)
     * @return if there is a value bound to the current thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static boolean hasResource(Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doGetResource(getInternalState().getResources(), actualKey);
        return (value != null);
    }

    /**
     * Retrieve a resource for the given key that is bound to the current thread.
     *
     * @param key the key to check (usually the resource factory)
     * @return a value bound to the current thread (usually the active
     * resource object), or {@code null} if none
     * @see ResourceTransactionManager#getResourceFactory()
     */
    @Nullable
    public static Object getResource(Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doGetResource(getInternalState().getResources(), actualKey);
        if (value != null && LOG.isTraceEnabled()) {
            LOG.trace("Retrieved value [" + value + "] for key [" + actualKey + "] bound to thread [" +
                Thread.currentThread().getName() + "]");
        }
        return value;
    }

    @NonNull
    public static <T> T getResourceOrDefault(Object key, @NonNull T defaultValue) {
        T resource = (T) getResource(key);
        return resource == null ? defaultValue : resource;
    }

    /**
     * Actually check the value of the resource that is bound for the given key.
     */
    @Nullable
    private static <T> T doGetResource(@Nullable Map<Object, T> map, @NonNull Object actualKey) {
        if (map == null) {
            return null;
        }
        T value = map.get(actualKey);
        // Transparently remove ResourceHolder that was marked as void...
        if (value instanceof ResourceHolder && ((ResourceHolder) value).isVoid()) {
            map.remove(actualKey);
            value = null;
        }
        return value;
    }

    /**
     * Bind the given resource for the given key to the current thread.
     *
     * @param key   the key to bind the value to (usually the resource factory)
     * @param value the value to bind (usually the active resource object)
     * @throws IllegalStateException if there is already a value bound to the thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static void bindResource(Object key, Object value) throws IllegalStateException {
        bindResource(getOrCreateInternalState().getResources(), key, value);
    }

    public static void rebindResource(Object key, Object value) throws IllegalStateException {
        Object resource = getResource(key);
        if (resource == null) {
            bindResource(key, value);
        } else if (resource != value) {
            unbindResource(key);
            bindResource(key, value);
        }
    }

    private static <T> void bindResource(Map<Object, T> map, Object key, T value) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Objects.requireNonNull(value, "Value must not be null");
        Object oldValue = map.put(actualKey, value);
        // Transparently suppress a ResourceHolder that was marked as void...
        if (oldValue instanceof ResourceHolder && ((ResourceHolder) oldValue).isVoid()) {
            oldValue = null;
        }
        if (oldValue != null) {
            throw new IllegalStateException("Already value [" + oldValue + "] for key [" +
                actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Bound value [" + value + "] for key [" + actualKey + "] to thread [" +
                Thread.currentThread().getName() + "]");
        }
    }

    /**
     * Unbind a resource for the given key from the current thread.
     *
     * @param key the key to unbind (usually the resource factory)
     * @return the previously bound value (usually the active resource object)
     * @throws IllegalStateException if there is no value bound to the thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static Object unbindResource(Object key) throws IllegalStateException {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doUnbindResource(getInternalState().getResources(), actualKey);
        if (value == null) {
            throw new IllegalStateException(
                "No value for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        return value;
    }

    /**
     * Unbind a resource for the given key from the current thread.
     *
     * @param key the key to unbind (usually the resource factory)
     * @return the previously bound value, or {@code null} if none bound
     */
    @Nullable
    public static Object unbindResourceIfPossible(Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        return doUnbindResource(getInternalState().getResources(), actualKey);
    }

    /**
     * Actually remove the value of the resource that is bound for the given key.
     */
    @Nullable
    private static <T> T doUnbindResource(@Nullable Map<Object, T> map, @NonNull Object actualKey) {
        T value = map == null ? null : map.remove(actualKey);
        // Transparently suppress a ResourceHolder that was marked as void...
        if (value instanceof ResourceHolder && ((ResourceHolder) value).isVoid()) {
            value = null;
        }
        if (value != null && LOG.isTraceEnabled()) {
            LOG.trace("Removed value [" + value + "] for key [" + actualKey + "] from thread [" +
                Thread.currentThread().getName() + "]");
        }
        return value;
    }

    //-------------------------------------------------------------------------
    // Management of transaction synchronizations
    //-------------------------------------------------------------------------

    public static void bindSynchronousTransactionState(@NonNull Object key, @NonNull SynchronousTransactionState state) {
        bindResource(getOrCreateInternalState().getStates(), key, state);
    }

    public static SynchronousTransactionState unbindSynchronousTransactionState(Object key) throws IllegalStateException {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        SynchronousTransactionState value = doUnbindResource(getInternalState().getStates(), actualKey);
        if (value == null) {
            throw new IllegalStateException(
                "No value for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        return value;
    }

    @Nullable
    public static SynchronousTransactionState getSynchronousTransactionState(@NonNull Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        SynchronousTransactionState value = doGetResource(getInternalState().getStates(), actualKey);
        if (value != null && LOG.isTraceEnabled()) {
            LOG.trace("Retrieved value [" + value + "] for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        return value;
    }

    @NonNull
    public static SynchronousTransactionState getRequiredSynchronousTransactionState(@NonNull Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        SynchronousTransactionState value = doGetResource(getInternalState().getStates(), actualKey);
        if (value == null) {
            throw new IllegalStateException("No value for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Retrieved value [" + value + "] for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        return value;
    }

    @NonNull
    public static SynchronousTransactionState getSynchronousTransactionStateOrCreate(@NonNull Object key, Supplier<SynchronousTransactionState> creator) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        SynchronousTransactionState value = doGetResource(getOrCreateInternalState().getStates(), actualKey);
        if (value != null && LOG.isTraceEnabled()) {
            LOG.trace("Retrieved value [" + value + "] for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        if (value == null) {
            value = creator.get();
            bindSynchronousTransactionState(actualKey, value);
        }
        return value;
    }

    @Nullable
    private static SynchronousTransactionState findDefaultState() {
        Map<Object, SynchronousTransactionState> states = getInternalState().getStates();
        if (states.isEmpty()) {
            return null;
        }
        if (states.size() == 1) {
            return states.values().iterator().next();
        }
        SynchronousTransactionState synchronousTransactionState = states.get(DEFAULT_STATE_KEY);
        if (synchronousTransactionState != null) {
            return synchronousTransactionState;
        }
        throw new IllegalStateException("Multiple synchronous transaction states found!");
    }

    @NonNull
    private static SynchronousTransactionState getOrEmptyDefaultState() {
        SynchronousTransactionState synchronousTransactionState = findDefaultState();
        return synchronousTransactionState == null ? new DefaultSynchronousTransactionState() : synchronousTransactionState;
    }

    @NonNull
    private static SynchronousTransactionState getRequiredDefaultState() {
        SynchronousTransactionState synchronousTransactionState = findDefaultState();
        if (synchronousTransactionState == null) {
            throw new IllegalStateException("Cannot find default synchronous transaction state!");
        }
        return synchronousTransactionState;
    }

    /**
     * Return if transaction synchronization is active for the current thread.
     * Can be called before register to avoid unnecessary instance creation.
     *
     * @return True if a synchronization is active
     * @see #registerSynchronization
     * @deprecated use {@link #getSynchronousTransactionState(Object)}
     */
    @Deprecated
    public static boolean isSynchronizationActive() {
        return getOrEmptyDefaultState().isSynchronizationActive();
    }


    /**
     * Register a new transaction synchronization for the current thread.
     * Typically called by resource management code.
     * <p>Note that synchronizations can implement the
     * {@link io.micronaut.core.order.Ordered} interface.
     * They will be executed in an order according to their order value (if any).
     *
     * @param synchronization the synchronization object to register
     * @throws IllegalStateException if transaction synchronization is not active
     * @see io.micronaut.core.order.Ordered
     * @deprecated use {@link #getSynchronousTransactionState(Object)}
     */
    @Deprecated
    public static void registerSynchronization(TransactionSynchronization synchronization)
        throws IllegalStateException {
        getRequiredDefaultState().registerSynchronization(synchronization);
    }



    //-------------------------------------------------------------------------
    // Exposure of transaction characteristics
    //-------------------------------------------------------------------------

    /**
     * Get the existing state.
     *
     * @return The state
     * @since 3.3
     */
    @Internal
    @Nullable
    public static TransactionSynchronizationState getState() {
        return STATE.get();
    }

    /**
     * Get existing or create new associated state.
     *
     * @return The state
     * @since 3.3
     */
    @Internal
    @NonNull
    public static TransactionSynchronizationState getOrCreateState() {
        return getOrCreateInternalState();
    }

    /**
     * Restore the state from the thread local.
     *
     * @param state The state
     * @since 3.3
     */
    @Internal
    public static void setState(@Nullable TransactionSynchronizationState state) {
        if (state == null) {
            STATE.remove();
            return;
        }
        if (state instanceof MutableTransactionSynchronizationState) {
            MutableTransactionSynchronizationState mutableState = (MutableTransactionSynchronizationState) state;
            STATE.set(mutableState);
        } else {
            throw new IllegalStateException("Unknown state: " + state);
        }
    }

    /**
     * Execute provided supplier with setup state in the thread-local. Afterwards update the state with the modifications done to it.
     *
     * @param state    The state
     * @param supplier The supplier to be executed
     * @param <T>      The supplied type
     * @return The suppler return value
     * @author Denis Stepanov
     * @since 3.4.0
     */
    @Internal
    public static <T> T withState(@Nullable TransactionSynchronizationState state, Supplier<T> supplier) {
        TransactionSynchronizationState previousState = getState();
        try {
            setState(state);
            return supplier.get();
        } finally {
            setState(previousState);
        }
    }

    /**
     * Decorate completion stage supplier with the transaction state.
     *
     * @param state    The state
     * @param supplier The supplier to be executed
     * @param <T>      The supplied type
     * @return The suppler return value
     * @author Denis Stepanov
     * @since 3.5.0
     */
    @Internal
    public static <T> CompletionStage<T> decorateCompletionStage(@Nullable TransactionSynchronizationState state, Supplier<CompletionStage<T>> supplier) {
        if (state == null) {
            return supplier.get();
        }
        try (TransactionSynchronizationStateOp ignore = withState(state)) {
            CompletableFuture<T> completableFuture = new CompletableFuture<>();
            supplier.get().whenComplete((value, throwable) -> {
                try (TransactionSynchronizationStateOp ignore2 = withState(state)) {
                    if (throwable != null) {
                        completableFuture.completeExceptionally(throwable);
                    } else {
                        completableFuture.complete(value);
                    }
                }
            });
            return completableFuture;
        }
    }

    /**
     * Execute provided supplier with setup state in the thread-local. Afterwards update the state with the modifications done to it.
     *
     * @param state The state
     * @return The suppler return value
     * @author Denis Stepanov
     * @since 3.5.0
     */
    @Internal
    public static TransactionSynchronizationStateOp withState(@Nullable TransactionSynchronizationState state) {
        TransactionSynchronizationState previousState = getState();
        setState(state);
        return new TransactionSynchronizationStateOp() {

            @Override
            public TransactionSynchronizationState getState() {
                return state;
            }

            @Override
            public TransactionSynchronizationState getOrCreateState() {
                return TransactionSynchronizationManager.getOrCreateState();
            }

            @Override
            public void close() {
                setState(previousState);
            }
        };
    }

    /**
     * Execute provided supplier with setup state in the thread-local. Make sure the thread-local state is cleanup afterwards.
     *
     * @return 3.5.0
     */
    @Internal
    public static TransactionSynchronizationStateOp withGuardedState() {
        TransactionSynchronizationState previousState = getState();
        TransactionSynchronizationState newState = previousState == null ? null : previousState.copy();
        return withState(newState);
    }

    /**
     * Decorate the supplier with possible propagated state in thread-local.
     * <p>
     * It's used to propagated TX state down to the async functions.
     *
     * @param supplier The supplier to be decorated
     * @param <T>      The supplied type
     * @return The decorated supplier
     * @author Denis Stepanov
     * @since 3.4.0
     */
    @Internal
    public static <T> Supplier<T> decorateToPropagateState(Supplier<T> supplier) {
        TransactionSynchronizationState state = STATE.get();
        if (state == null) {
            return supplier;
        }
        TransactionSynchronizationState stateCopy = state.copy();
        return () -> {
            try (TransactionSynchronizationStateOp ignore = withState(stateCopy)) {
                return supplier.get();
            }
        };
    }

    /**
     * The synchronization state.
     */
    @Internal
    public interface TransactionSynchronizationState {

        TransactionSynchronizationState copy();
    }


    /**
     * The transaction synchronization state autocloseable operation.
     */
    public interface TransactionSynchronizationStateOp extends AutoCloseable {

        /**
         * @return the state
         */
        @Nullable
        TransactionSynchronizationState getState();

        /**
         * @return the state
         */
        @NonNull
        TransactionSynchronizationState getOrCreateState();

        @Override
        void close();
    }

    /**
     * The copy-state of the thread-local values.
     *
     * @author Denis Stepanov
     * @since 3.4.0
     */
    private static final class MutableTransactionSynchronizationState implements TransactionSynchronizationState {
        private final Map<Object, Object> resources;
        private final Map<Object, SynchronousTransactionState> states;

        private MutableTransactionSynchronizationState() {
            this(new HashMap<>(2, 1), new HashMap<>(2, 1));
        }

        private MutableTransactionSynchronizationState(Map<Object, Object> resources, Map<Object, SynchronousTransactionState> states) {
            this.resources = resources;
            this.states = states;
        }

        @NonNull
        public synchronized Map<Object, Object> getResources() {
            return resources;
        }

        @NonNull
        public synchronized Map<Object, SynchronousTransactionState> getStates() {
            return states;
        }

        @Override
        public MutableTransactionSynchronizationState copy() {
            return new MutableTransactionSynchronizationState(new HashMap<>(resources), new HashMap<>(states));
        }
    }

}

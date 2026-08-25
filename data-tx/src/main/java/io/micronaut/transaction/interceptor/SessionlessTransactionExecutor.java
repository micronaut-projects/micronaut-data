/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.sessionless.SessionlessTransactionHandler;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Internal helper for synchronous sessionless transaction execution.
 *
 * <p>The sessionless lifecycle is driven from the transactional callback rather than from a
 * transaction manager, so no transaction manager implementation has to be specialized or replaced.</p>
 *
 * @since 5.2
 */
@Internal
final class SessionlessTransactionExecutor {

    private static final String UNQUALIFIED = "";

    private final BeanLocator beanLocator;
    private final ConcurrentMap<String, Optional<SessionlessTransactionHandler>> handlers = new ConcurrentHashMap<>();

    SessionlessTransactionExecutor(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Nullable
    <C> Object execute(TransactionOperations<C> transactionManager,
                       TransactionDefinition definition,
                       MethodInvocationContext<Object, Object> context,
                       OracleTransactional.Sessionless mode,
                       @Nullable String dataSourceName) {
        SessionlessTransactionHandler handler = findHandler(dataSourceName);
        if (handler == null) {
            throw new TransactionSuspensionNotSupportedException(
                "Oracle sessionless transaction mode '" + mode + "' requires Oracle sessionless transaction support"
            );
        }
        return transactionManager.<@Nullable Object>execute(definition, new TransactionCallback<C, @Nullable Object>() {
            @Override
            public @Nullable Object call(TransactionStatus<C> status) throws Exception {
                if (!status.isNewTransaction()) {
                    throw new TransactionUsageException(
                        "Oracle sessionless transaction mode '" + mode + "' cannot join an existing transaction"
                    );
                }
                handler.begin(status, definition);
                return context.proceed();
            }
        });
    }

    @Nullable
    private SessionlessTransactionHandler findHandler(@Nullable String dataSourceName) {
        return handlers
            .computeIfAbsent(dataSourceName == null ? UNQUALIFIED : dataSourceName, this::resolveHandler)
            .orElse(null);
    }

    /**
     * Resolves the handler the same way {@code DefaultTransactionOperationsRegistry} resolves the
     * transaction manager: by name when the transactional method declares a datasource, and unqualified
     * otherwise. The handler is an {@code @EachBean(DataSource.class)} bean, so its qualifier is the
     * datasource name; assuming "default" would break every application whose datasource is named
     * something else.
     *
     * @param dataSourceName The declared datasource name, or {@link #UNQUALIFIED} when none was declared
     * @return The handler, if one is configured
     */
    @NonNull
    private Optional<SessionlessTransactionHandler> resolveHandler(@NonNull String dataSourceName) {
        if (UNQUALIFIED.equals(dataSourceName)) {
            return beanLocator.findBean(SessionlessTransactionHandler.class);
        }
        return beanLocator.findBean(SessionlessTransactionHandler.class, Qualifiers.byName(dataSourceName));
    }
}

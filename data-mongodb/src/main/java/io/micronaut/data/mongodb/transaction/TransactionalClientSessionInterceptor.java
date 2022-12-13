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
package io.micronaut.data.mongodb.transaction;

import com.mongodb.client.ClientSession;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.exceptions.NoTransactionException;

/**
 * An interceptor that allows injecting a {@link ClientSession} that acts a proxy to lookup the connection for the current transaction.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Requires(classes = ClientSession.class, beans = MongoSynchronousTransactionManagerImpl.class)
@Prototype
public final class TransactionalClientSessionInterceptor implements MethodInterceptor<ClientSession, Object> {

    private final MongoSynchronousTransactionManagerImpl transactionManager;

    /**
     * Default constructor.
     *
     * @param transactionManager The transactionManager
     */
    @Internal
    TransactionalClientSessionInterceptor(MongoSynchronousTransactionManagerImpl transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object intercept(MethodInvocationContext<ClientSession, Object> context) {
        ClientSession clientSession = transactionManager.findClientSession();
        if (clientSession == null) {
            throw new NoTransactionException("No current transaction present. Consider declaring @Transactional on the surrounding method");
        }
        final ExecutableMethod<ClientSession, Object> method = context.getExecutableMethod();

        if (method.getName().equals("close")) {
            // Handle close method: only close if not within a transaction.
            transactionManager.closeClientSession();
            return null;
        }

        return method.invoke(clientSession, context.getParameterValues());
    }
}

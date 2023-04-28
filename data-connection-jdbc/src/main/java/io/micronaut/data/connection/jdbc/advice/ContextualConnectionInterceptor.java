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
package io.micronaut.data.connection.jdbc.advice;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.jdbc.operations.DataSourceConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.inject.ExecutableMethod;

import java.sql.Connection;
import java.util.Optional;

/**
 * An interceptor that allows injecting a {@link Connection} that acts a proxy to lookup the connection for the current transaction.
 *
 * @author graemerocher
 * @since 1.0
 */
@Prototype
public final class ContextualConnectionInterceptor implements MethodInterceptor<Connection, Object> {

    private final DataSourceConnectionOperations dataSourceConnectionOperations;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     * @param qualifier   The qualifier
     */
    @Internal
    ContextualConnectionInterceptor(BeanContext beanContext, Qualifier<DataSourceConnectionOperations> qualifier) {
        dataSourceConnectionOperations = beanContext.getBean(DataSourceConnectionOperations.class, qualifier);
    }

    @Override
    public Object intercept(MethodInvocationContext<Connection, Object> context) {
        Optional<ConnectionStatus<Connection>> connectionStatus = dataSourceConnectionOperations.findConnectionStatus();
        if (connectionStatus.isEmpty()) {
            throw NoConnectionException.notFoundInAdvice();
        }
        Connection connection = connectionStatus.get().getConnection();
        final ExecutableMethod<Connection, Object> method = context.getExecutableMethod();
        if (method.getName().equals("close")) {
            // Close method is not allowed
            return null;
        }

        return method.invoke(connection, context.getParameterValues());
    }
}

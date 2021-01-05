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
package io.micronaut.transaction.jdbc;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.jdbc.exceptions.CannotGetJdbcConnectionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * An interceptor that allows injecting a {@link Connection} that acts a proxy to lookup the connection for the current transaction.
 *
 * @author graemerocher
 * @since 1.0
 */
@Prototype
public final class TransactionalConnectionInterceptor implements MethodInterceptor<Connection, Object> {

    private final DataSource dataSource;
    private boolean closed;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     * @param qualifier   The qualifier
     */
    @Internal
    TransactionalConnectionInterceptor(BeanContext beanContext, Qualifier<DataSource> qualifier) {
        DataSource dataSource = beanContext.getBean(DataSource.class, qualifier);
        if (dataSource instanceof DelegatingDataSource) {
            dataSource = ((DelegatingDataSource) dataSource).getTargetDataSource();
        }
        this.dataSource = dataSource;
    }

    @Override
    public Object intercept(MethodInvocationContext<Connection, Object> context) {
        Connection connection;
        try {
            connection = DataSourceUtils.getConnection(dataSource, false);
        } catch (CannotGetJdbcConnectionException e) {
            throw new NoTransactionException("No current transaction present. Consider declaring @Transactional on the surrounding method", e);
        }
        final ExecutableMethod<Connection, Object> method = context.getExecutableMethod();

        if (method.getName().equals("close")) {
            // Handle close method: only close if not within a transaction.
            try {
                DataSourceUtils.doReleaseConnection(connection, this.dataSource);
            } catch (SQLException e) {
                throw new CannotGetJdbcConnectionException("Failed to release connection: " + e.getMessage(), e);
            }
            return null;
        }

        return method.invoke(connection, context.getParameterValues());
    }
}

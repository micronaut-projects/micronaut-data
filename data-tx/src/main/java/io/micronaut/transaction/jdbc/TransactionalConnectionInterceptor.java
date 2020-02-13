package io.micronaut.transaction.jdbc;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.jdbc.exceptions.CannotGetJdbcConnectionException;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * An interceptor that allows injecting a {@link Connection} that acts a proxy to lookup the connection for the current transaction.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
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

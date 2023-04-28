package io.micronaut.data.hibernate.connection;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.jdbc.operations.DataSourceConnectionOperations;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionBuilder;
import org.hibernate.SessionFactory;

import javax.sql.DataSource;

@Internal
@EachBean(DataSource.class)
@Replaces(DataSourceConnectionOperations.class)
public final class HibernateConnectionOperations extends AbstractConnectionOperations<Session> {

    private final SessionFactory sessionFactory;
    @Nullable
    private final Interceptor entityInterceptor;

    public HibernateConnectionOperations(@Parameter SessionFactory sessionFactory,
                                         @Nullable Interceptor entityInterceptor) {
        this.sessionFactory = sessionFactory;
        this.entityInterceptor = entityInterceptor;
    }

    @Override
    protected Session openConnection(ConnectionDefinition definition) {
        SessionBuilder sessionBuilder = sessionFactory.withOptions();
        if (entityInterceptor != null) {
            sessionBuilder = sessionBuilder.interceptor(entityInterceptor);
        }
        return sessionBuilder.openSession();
    }

    @Override
    protected void setupConnection(ConnectionStatus<Session> connectionStatus) {
    }

    @Override
    protected void closeConnection(ConnectionStatus<Session> connectionStatus) {
        connectionStatus.getConnection().close();
    }
}

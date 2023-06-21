/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.hibernate.connection;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import io.micronaut.data.hibernate.conf.RequiresSyncHibernate;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionBuilder;
import org.hibernate.SessionFactory;

/**
 * The Hibernate connection operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Order(100)
@Internal
@RequiresSyncHibernate
@EachBean(SessionFactory.class)
public final class HibernateConnectionOperations extends AbstractConnectionOperations<Session> {

    private final SessionFactory sessionFactory;
    @Nullable
    private final Interceptor entityInterceptor;

    public HibernateConnectionOperations(SessionFactory sessionFactory,
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

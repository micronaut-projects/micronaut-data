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
package io.micronaut.transaction.hibernate;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.context.spi.CurrentSessionContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import java.util.Optional;

/**
 * Implementation of Hibernate 3.1's {@link CurrentSessionContext} interface
 * that delegates to {@link ConnectionOperations} for providing a current {@link Session}.
 *
 * <p>This CurrentSessionContext implementation can also be specified in custom
 * SessionFactory setup through the "hibernate.current_session_context_class"
 * property, with the fully qualified name of this class as value.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 4.0.0
 */
@TypeHint(MicronautSessionContext.class)
public class MicronautSessionContext implements CurrentSessionContext {

    private final BeanProvider<ConnectionOperations<Session>> connectionOperationProvider;

    /**
     * Create a new MicronautSessionContext for the given {@link SessionFactoryImplementor}.
     *
     * @param sessionFactory the SessionFactoryImplementor
     */
    public MicronautSessionContext(SessionFactoryImplementor sessionFactory) {
        this.connectionOperationProvider = sessionFactory.getServiceRegistry()
            .getService(ConnectionOperationsProviderService.class)
            .provider();
    }

    @Override
    public Session currentSession() throws HibernateException {
        Optional<ConnectionStatus<Session>> connectionStatus = connectionOperationProvider.get().findConnectionStatus();
        if (connectionStatus.isEmpty()) {
            throw new HibernateException("Could not obtain transaction-synchronized Session for current thread");
        }
        return connectionStatus.get().getConnection();
    }

}

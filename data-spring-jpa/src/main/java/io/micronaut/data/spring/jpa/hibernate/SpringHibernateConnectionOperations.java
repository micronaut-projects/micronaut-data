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
package io.micronaut.data.spring.jpa.hibernate;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.support.DefaultConnectionStatus;
import io.micronaut.data.hibernate.connection.HibernateConnectionOperations;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.orm.hibernate5.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.function.Function;

/**
 * Spring JDBC Hibernate Session operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@EachBean(SessionFactory.class)
@Replaces(HibernateConnectionOperations.class)
// TODO: We should avoid using @Replaces, there should be a way to use different data sources with Micronaut and Spring TX
public final class SpringHibernateConnectionOperations implements ConnectionOperations<Session> {

    private final SessionFactory sessionFactory;

    SpringHibernateConnectionOperations(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Optional<ConnectionStatus<Session>> findConnectionStatus() {
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
        if (sessionHolder != null && sessionHolder.getEntityManager() != null) {
            return Optional.of(createStatus(sessionHolder.getSession()));
        }
        return Optional.empty();
    }

    @Override
    public <R> R execute(ConnectionDefinition definition, Function<ConnectionStatus<Session>, R> callback) {
        return new HibernateTemplate(sessionFactory).execute(session -> callback.apply(createStatus(session)));
    }

    private DefaultConnectionStatus<Session> createStatus(Session session) {
        return new DefaultConnectionStatus<>(
            session,
            ConnectionDefinition.DEFAULT,
            true
        );
    }

}

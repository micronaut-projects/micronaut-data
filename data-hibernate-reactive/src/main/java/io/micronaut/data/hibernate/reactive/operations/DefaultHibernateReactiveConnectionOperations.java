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
package io.micronaut.data.hibernate.reactive.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.support.AbstractReactorReactiveConnectionOperations;
import io.micronaut.data.hibernate.conf.RequiresReactiveHibernate;
import org.hibernate.SessionFactory;
import org.hibernate.reactive.stage.Stage;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default Hibernate reactive connection operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@RequiresReactiveHibernate
@EachBean(SessionFactory.class)
@Internal
final class DefaultHibernateReactiveConnectionOperations extends AbstractReactorReactiveConnectionOperations<Stage.Session> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHibernateReactiveConnectionOperations.class);

    private final ReactiveHibernateHelper helper;
    private final String serverName;

    DefaultHibernateReactiveConnectionOperations(@Parameter String serverName, SessionFactory sessionFactory) {
        this.helper = new ReactiveHibernateHelper(sessionFactory.unwrap(Stage.SessionFactory.class));
        this.serverName = serverName;
    }

    @Override
    protected Publisher<Stage.Session> openConnection(ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Opening Connection for Hibernate Reactive configuration: {} and definition: {}", serverName, definition);
        }
        return helper.openSession();
    }

    @Override
    protected Publisher<Void> closeConnection(Stage.Session session, ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing Connection for Hibernate Reactive configuration: {} and definition: {}", serverName, definition);
        }
        return helper.closeSession(session);
    }
}

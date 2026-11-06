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
package io.micronaut.data.neo4j.session;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.support.AbstractReactorConnectionOperations;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Opens a reactive session of the Neo4j driver for each reactive connection.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@EachBean(Neo4jDriverRegistration.class)
@Internal
final class DefaultNeo4jReactorConnectionOperations extends AbstractReactorConnectionOperations<Neo4jReactiveConnection>
    implements Neo4jReactorConnectionOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNeo4jReactorConnectionOperations.class);

    private final Driver driver;
    private final Neo4jSessionConfigurer sessions;

    DefaultNeo4jReactorConnectionOperations(@Parameter Neo4jDriverRegistration registration, @Parameter Neo4jSessionConfigurer sessions) {
        this.driver = registration.driver();
        this.sessions = sessions;
    }

    @Override
    protected Publisher<Neo4jReactiveConnection> openConnection(ConnectionDefinition definition) {
        return Mono.fromSupplier(() -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Opening reactive Neo4j session for definition: {}", definition);
            }
            return new Neo4jReactiveConnection(driver.session(ReactiveSession.class, sessions.sessionConfig()));
        });
    }

    @Override
    protected Publisher<Void> closeConnection(Neo4jReactiveConnection connection, ConnectionDefinition definition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Closing reactive Neo4j session for definition: {}", definition);
        }
        return Mono.from(connection.getSession().<Void>close());
    }
}

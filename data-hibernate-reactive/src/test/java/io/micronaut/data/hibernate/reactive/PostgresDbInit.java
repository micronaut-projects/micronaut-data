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
package io.micronaut.data.hibernate.reactive;

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Adds a local-only readiness probe for Postgres-backed Hibernate Reactive tests.
 * <p>
 * On some local Docker runtimes, especially Rancher Desktop, the Postgres
 * container may be reported as started before the published localhost port is
 * reliably reachable. Hibernate Reactive then attempts to create the Vert.x
 * Postgres pool immediately and can fail with {@code Connection refused}.
 * <p>
 * Hibernate Reactive test resources resolve the actual container endpoint into
 * {@code jpa.<name>.properties.hibernate.connection.*}. Listening on the JPA
 * configuration gives access to those final resolved values for both the
 * default and named reactive datasources before Hibernate starts opening
 * connections.
 * <p>
 * The listener probes the resolved Postgres JDBC endpoint before Hibernate
 * Reactive builds its infrastructure. It is intentionally disabled in GitHub
 * Actions, where the timing issue has not been observed.
 */
@Requires(missingProperty = "github.workflow")
@Singleton
public class PostgresDbInit implements BeanCreatedEventListener<JpaConfiguration>, Ordered {

    private static final String CONNECTION_URL = "hibernate.connection.url";
    private static final String CONNECTION_USERNAME = "hibernate.connection.username";
    private static final String CONNECTION_PASSWORD = "hibernate.connection.password";
    private static final String CONNECTION_DB_TYPE = "hibernate.connection.db-type";

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    public JpaConfiguration onCreated(BeanCreatedEvent<JpaConfiguration> event) {
        JpaConfiguration configuration = event.getBean();
        // Mirror the bean-level guard with the raw environment variable used by test specs.
        if (System.getenv("GITHUB_WORKFLOW") != null) {
            return configuration;
        }
        if (!configuration.isReactive()) {
            return configuration;
        }
        Map<String, Object> properties = configuration.getProperties();
        if (properties == null || properties.isEmpty()) {
            return configuration;
        }
        if (!"postgres".equalsIgnoreCase(stringValue(properties.get(CONNECTION_DB_TYPE)))) {
            return configuration;
        }

        String url = requireValue(stringValue(properties.get(CONNECTION_URL)), CONNECTION_URL);

        final Properties info = new Properties();
        info.put("user", requireValue(stringValue(properties.get(CONNECTION_USERNAME)), CONNECTION_USERNAME));
        info.put("password", requireValue(stringValue(properties.get(CONNECTION_PASSWORD)), CONNECTION_PASSWORD));

        // Wait until the host-side port forward is reachable before Hibernate Reactive
        // performs schema creation or opens its first reactive connection.
        int attempts = 30;
        SQLException last = null;
        while (attempts-- > 0) {
            try (Connection ignored = DriverManager.getConnection(url, info)) {
                last = null;
                break;
            } catch (SQLException e) {
                last = e;
                try {
                    // Rancher local testing delay
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        if (last != null) {
            throw new RuntimeException(last);
        }
        return configuration;
    }

    private static String requireValue(String value, String optionName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required Postgres option: " + optionName);
        }
        return value;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

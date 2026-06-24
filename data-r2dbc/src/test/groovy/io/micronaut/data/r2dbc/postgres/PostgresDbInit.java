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
package io.micronaut.data.r2dbc.postgres;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.order.Ordered;
import io.micronaut.r2dbc.DefaultBasicR2dbcProperties;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import jakarta.inject.Singleton;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Adds a local-only readiness delay for Postgres-backed R2DBC tests.
 * <p>
 * Some local Docker runtimes, especially Rancher Desktop, report the Postgres
 * container as started before the published localhost port is consistently
 * reachable from the host. The plain R2DBC Postgres tests do not have a
 * JDBC-side bootstrap step like the JDBC and Hibernate test suites, so schema
 * generation may attempt the first R2DBC connection during that short window
 * and fail with {@code Connection refused}.
 * <p>
 * This listener probes the resolved Postgres JDBC endpoint before the
 * {@link io.micronaut.data.r2dbc.config.R2dbcSchemaGenerator} starts using the
 * R2DBC connection factory. It is intentionally disabled in GitHub Actions,
 * where the timing issue has not been observed.
 */
@Requires(missingProperty = "github.workflow")
@Singleton
public class PostgresDbInit implements BeanCreatedEventListener<DefaultBasicR2dbcProperties>, Ordered {

    @Override
    public int getOrder() {
        return -10;
    }

    @Override
    public DefaultBasicR2dbcProperties onCreated(BeanCreatedEvent<DefaultBasicR2dbcProperties> event) {
        DefaultBasicR2dbcProperties configuration = event.getBean();
        // Mirror the bean-level guard with the raw environment variable used by test specs.
        if (System.getenv("GITHUB_WORKFLOW") != null) {
            return configuration;
        }
        ConnectionFactoryOptions options = configuration.getBuilder().build();

        Object driver = options.getValue(Option.valueOf("driver"));
        // Only add the readiness probe for Postgres-backed R2DBC configurations.
        if (!(driver instanceof String driverName) || !driverName.toLowerCase(Locale.ROOT).contains("postgres")) {
            return configuration;
        }

        final Properties info = new Properties();
        info.put("user", requireOption(options, "user", String.class));
        info.put("password", requireOption(options, "password", String.class));

        String host = requireOption(options, "host", String.class);
        Integer port = requireOption(options, "port", Integer.class);
        String database = requireOption(options, "database", String.class);
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        // Wait until the host-side port forward is reachable before schema generation begins.
        int attempts = 30;
        SQLException last = null;
        while (attempts-- > 0) {
            try (Connection connection = DriverManager.getConnection(url, info)) {
                try (CallableStatement statement = connection.prepareCall("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";")) {
                    statement.execute();
                }
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

    private static <T> T requireOption(ConnectionFactoryOptions options, String optionName, Class<T> type) {
        // Fail fast with a precise message if the resolved test-resources options are incomplete.
        Object value = options.getValue(Option.valueOf(optionName));
        if (value == null) {
            throw new IllegalStateException("Missing required R2DBC option: " + optionName);
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Invalid R2DBC option type for " + optionName + ": " + value.getClass().getName());
        }
        return type.cast(value);
    }
}

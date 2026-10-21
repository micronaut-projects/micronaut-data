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
package io.micronaut.data.jdbc.notification.postgres;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jdbc.BasicJdbcConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.postgresql.Driver;
import org.postgresql.PGProperty;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Opens dedicated PostgreSQL connections for notification subscriptions.
 *
 * <p>Each connection request resolves the current datasource configuration and creates a new
 * {@link PGSimpleDataSource}, including requests made during reconnection. The JDBC URL, credentials,
 * and driver properties are read again so configuration refreshes apply to subsequent connections.
 * Each request opens a physical connection outside the application connection pool.</p>
 *
 * <p>Credential changes must be visible through the datasource configuration bean or environment.
 * Authentication performed only by the application pool, including token generation, is not invoked
 * by this factory.</p>
 *
 * <p>Missing or zero connect, login, and socket-read timeouts are replaced with finite defaults
 * of 10, 30, and 30 seconds respectively. The connect timeout limits each TCP connection attempt;
 * the login timeout limits the caller's wait for a usable connection, including authentication;
 * and the socket-read timeout limits reads during operations such as {@code LISTEN} and {@code UNLISTEN}.
 * pgJDBC defaults login and socket-read timeouts to unlimited. Finite values allow stalled setup
 * operations to fail so reception can retry or finish shutdown. The connect timeout also replaces
 * an explicitly unlimited value, while retaining pgJDBC's default of 10 seconds.</p>
 *
 * <p>TCP keepalive is enabled unless explicitly configured. Its timing depends on the operating
 * system; the subscription also performs periodic JDBC connection validation to detect silent failures.</p>
 *
 * <p>Positive configured timeouts are preserved. These defaults apply only to notification connections.
 * They do not cancel JDBC operations immediately when shutdown is requested or provide an overall
 * shutdown deadline. Timed notification polling uses the subscription's poll timeout.</p>
 */
@Internal
@Singleton
@Requires(classes = PGSimpleDataSource.class)
class PostgresNotificationConnectionFactory {
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_LOGIN_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_SOCKET_TIMEOUT_SECONDS = 30;

    private final BeanContext beanContext;
    private final Environment environment;

    PostgresNotificationConnectionFactory(BeanContext beanContext, Environment environment) {
        this.beanContext = beanContext;
        this.environment = environment;
    }

    /**
     * Opens a new physical connection using the current datasource configuration.
     * The subscription owns the connection and is responsible for configuring and closing it.
     *
     * @param dataSourceName the datasource selected by the listeners
     * @return a new non-pooled connection to the datasource's database
     * @throws SQLException if the connection cannot be opened
     */
    Connection openConnection(String dataSourceName) throws SQLException {
        return createDataSource(dataSourceName).getConnection();
    }

    /**
     * Configures a datasource from the named datasource's JDBC URL, credentials, and driver properties.
     */
    private PGSimpleDataSource createDataSource(String dataSourceName) {
        BasicJdbcConfiguration configuration = beanContext.findBean(BasicJdbcConfiguration.class,
            Qualifiers.byName(dataSourceName)).orElse(null);
        String url = resolveUrl(dataSourceName, configuration);
        Properties properties = resolveProperties(dataSourceName, configuration);
        // Match pgJDBC's precedence: properties in the URL override separately supplied properties.
        Properties parsed = Driver.parseURL(url, properties);
        if (parsed == null) {
            throw new ConfigurationException("Invalid PostgreSQL notification JDBC URL for datasource [" + dataSourceName + "]");
        }
        ensureFiniteTimeout(parsed, PGProperty.CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT_SECONDS, dataSourceName);
        ensureFiniteTimeout(parsed, PGProperty.LOGIN_TIMEOUT, DEFAULT_LOGIN_TIMEOUT_SECONDS, dataSourceName);
        ensureFiniteTimeout(parsed, PGProperty.SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT_SECONDS, dataSourceName);
        if (parsed.getProperty(PGProperty.TCP_KEEP_ALIVE.getName()) == null) {
            PGProperty.TCP_KEEP_ALIVE.set(parsed, true);
        }
        PGSimpleDataSource source = new PGSimpleDataSource();
        for (PGProperty property : PGProperty.values()) {
            source.setProperty(property, parsed.getProperty(property.getName()));
        }
        return source;
    }

    /**
     * Replaces missing or unlimited timeouts after URL precedence has been applied.
     * Positive driver values are retained; invalid values fail before opening a connection.
     */
    private static void ensureFiniteTimeout(Properties properties, PGProperty property, int defaultSeconds, String dataSourceName) {
        String value = properties.getProperty(property.getName());
        if (value == null) {
            property.set(properties, defaultSeconds);
            return;
        }
        try {
            // pgJDBC accepts fractional login seconds but requires integer connect and socket seconds.
            double seconds = property == PGProperty.LOGIN_TIMEOUT ? Float.parseFloat(value) : Integer.parseInt(value);
            double millis = seconds * 1000;
            double maxMillis = property == PGProperty.LOGIN_TIMEOUT ? Long.MAX_VALUE : Integer.MAX_VALUE;
            if (!Double.isFinite(seconds) || seconds < 0 || (seconds > 0 && millis < 1) || millis >= maxMillis) {
                throw new NumberFormatException();
            }
            if (seconds == 0) {
                property.set(properties, defaultSeconds);
            }
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid PostgreSQL notification " + property.getName()
                + " for datasource [" + dataSourceName + "]: expected a finite non-negative timeout in seconds representable in milliseconds", e);
        }
    }

    /**
     * Resolves the JDBC URL from the datasource configuration bean, or from environment properties
     * when no configuration bean is available. Requires a PostgreSQL JDBC URL.
     */
    private String resolveUrl(String dataSourceName, @Nullable BasicJdbcConfiguration configuration) {
        String prefix = BasicJdbcConfiguration.PREFIX + "." + dataSourceName;
        String url = configuration == null
            ? environment.getProperty(prefix + ".url", String.class)
                .or(() -> environment.getProperty(prefix + ".jdbc-url", String.class)).orElse(null)
            : configuration.getConfiguredUrl();
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new ConfigurationException("PostgreSQL notifications require a PostgreSQL JDBC URL for datasource ["
                + dataSourceName + "]");
        }
        return url;
    }

    /**
     * Combines {@code db-properties}, {@code connection-properties}, and {@code data-source-properties}
     * in that order, with later values replacing earlier ones. Explicit username and password settings
     * then override credentials in those properties. The caller subsequently applies JDBC URL
     * properties, which take precedence over all values resolved here.
     */
    private Properties resolveProperties(String dataSourceName, @Nullable BasicJdbcConfiguration configuration) {
        String prefix = BasicJdbcConfiguration.PREFIX + "." + dataSourceName;
        Properties properties = new Properties();
        addProperties(properties, environment.getProperties(prefix + ".db-properties", StringConvention.RAW));
        environment.getProperty(prefix + ".connection-properties", String.class)
            .ifPresent(value -> addConnectionProperties(properties, value));
        addProperties(properties, environment.getProperties(prefix + ".data-source-properties", StringConvention.RAW));
        String username = configuration == null
            ? environment.getProperty(prefix + ".username", String.class).orElse(null)
            : configuration.getUsername();
        String password = configuration == null
            ? environment.getProperty(prefix + ".password", String.class).orElse(null)
            : configuration.getPassword();
        if (username != null) {
            PGProperty.USER.set(properties, username);
        }
        if (password != null) {
            PGProperty.PASSWORD.set(properties, password);
        }
        return properties;
    }

    /**
     * Adds semicolon-separated {@code key=value} entries, preserving any additional equals signs in values.
     */
    private static void addConnectionProperties(Properties properties, String value) {
        for (String entry : value.split(";", -1)) {
            int separator = entry.indexOf('=');
            if (separator > 0) {
                properties.setProperty(entry.substring(0, separator).trim(), entry.substring(separator + 1));
            }
        }
    }

    /**
     * Copies properties as strings while preserving the case of their names, as required by pgJDBC.
     */
    private static void addProperties(Properties properties, Map<String, Object> values) {
        values.forEach((key, value) -> properties.setProperty(key, value.toString()));
    }
}

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Opens dedicated PostgreSQL connections for notification subscriptions.
 *
 * <p>Creates one {@link PGSimpleDataSource} per datasource name on first use and caches it for
 * the lifetime of this factory. Its JDBC URL, credentials, and driver properties are resolved
 * at that time. Each connection request opens a new physical connection outside the application
 * connection pool, including requests made during reconnection.</p>
 */
@Singleton
@Requires(classes = PGSimpleDataSource.class)
final class PostgresNotificationConnectionFactory {
    private final BeanContext beanContext;
    private final Environment environment;
    private final ConcurrentMap<String, PGSimpleDataSource> dataSources = new ConcurrentHashMap<>();

    PostgresNotificationConnectionFactory(BeanContext beanContext, Environment environment) {
        this.beanContext = beanContext;
        this.environment = environment;
    }

    /**
     * Opens a new physical connection, creating the cached datasource on first use.
     * The subscription owns the connection and is responsible for configuring and closing it.
     *
     * @param dataSourceName the datasource selected by the listeners
     * @return a new non-pooled connection to the datasource's database
     * @throws SQLException if the connection cannot be opened
     */
    Connection openConnection(String dataSourceName) throws SQLException {
        return dataSources.computeIfAbsent(dataSourceName, this::createDataSource).getConnection();
    }

    /**
     * Configures a datasource from the named datasource's JDBC URL, credentials, and driver properties.
     * The caller caches the source after configuration succeeds.
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
        PGSimpleDataSource source = new PGSimpleDataSource();
        for (PGProperty property : PGProperty.values()) {
            source.setProperty(property, parsed.getProperty(property.getName()));
        }
        return source;
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

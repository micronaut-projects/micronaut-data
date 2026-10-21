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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.intercept.annotation.PostgresChangeListenerQuery;
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.notification.ChangeListenerRegistry;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Installs notification functions and triggers for tables created by JDBC schema generation.
 * The shared listener registry supplies channels and generated key mappings without instantiating listeners
 * or repository operations while schema creation is in progress. Installation errors fail startup.
 */
@Internal
@Singleton
@Requires(property = "micronaut.data.jdbc.notifications.postgres.schema-generate", value = "true")
public final class PostgresNotificationSchemaGenerator {
    private static final String FUNCTION_NAME = "micronaut_data_notify_change";
    private static final String RESOURCE = "/io/micronaut/data/jdbc/notification/postgres/create-notification-function.sql";

    private final ChangeListenerRegistry registry;
    private final Environment environment;

    /**
     * @param registry the discovered listener metadata grouped by datasource
     * @param environment the mapping placeholder resolver
     */
    public PostgresNotificationSchemaGenerator(ChangeListenerRegistry registry, Environment environment) {
        this.registry = registry;
        this.environment = environment;
    }

    /**
     * Installs objects only for PostgreSQL tables in the supplied schema-generation pass.
     * Deterministic trigger names allow repeated creation without accumulating duplicate triggers.
     * Functions are installed in each subscribed table's schema and are retained when the application stops.
     *
     * @param connection the schema-generation connection
     * @param configuration the datasource configuration
     * @param entities the entities included in this schema-generation pass
     * @throws SQLException if installation fails
     */
    public void generate(Connection connection, DataJdbcConfiguration configuration, PersistentEntity[] entities) throws SQLException {
        if (configuration.getDialect() != Dialect.POSTGRES
            || (configuration.getSchemaGenerate() != SchemaGenerate.CREATE && configuration.getSchemaGenerate() != SchemaGenerate.CREATE_DROP)) {
            return;
        }
        Map<Subscription, List<String>> subscriptions = subscriptions(connection, configuration.getName(), entities);
        if (subscriptions.isEmpty()) {
            return;
        }
        String function = readFunction();
        Set<String> installedSchemas = new LinkedHashSet<>();
        for (Map.Entry<Subscription, List<String>> entry : subscriptions.entrySet()) {
            Subscription subscription = entry.getKey();
            String qualifiedFunction = quote(subscription.schema()) + "." + quote(FUNCTION_NAME);
            if (installedSchemas.add(subscription.schema())) {
                execute(connection, function.replace("CREATE OR REPLACE FUNCTION " + FUNCTION_NAME + "()",
                    "CREATE OR REPLACE FUNCTION " + qualifiedFunction + "()"));
            }
            String table = quote(subscription.schema()) + "." + quote(subscription.table());
            String arguments = literal(subscription.channel()) + ", " + entry.getValue().stream()
                .map(PostgresNotificationSchemaGenerator::literal).collect(Collectors.joining(", "));
            String name = triggerName(subscription);
            createTrigger(connection, table, name + "_rows", "INSERT OR UPDATE OR DELETE", "ROW", qualifiedFunction, arguments);
            createTrigger(connection, table, name + "_truncate", "TRUNCATE", "STATEMENT", qualifiedFunction, arguments);
        }
    }

    private Map<Subscription, List<String>> subscriptions(Connection connection, String datasource, PersistentEntity[] entities) throws SQLException {
        Map<String, PersistentEntity> mappings = Arrays.stream(entities)
            .collect(Collectors.toMap(PersistentEntity::getName, entity -> entity));
        Map<Subscription, List<String>> subscriptions = new LinkedHashMap<>();
        for (ChangeListenerMethod listener : registry.getListeners(datasource)) {
            ExecutableMethod<?, ?> method = listener.method();
            if (!method.hasStereotype(PostgresChangeNotification.class)) {
                continue;
            }
            Class<?> entityType = listener.entityArgument().getType();
            PersistentEntity entity = mappings.get(entityType.getName());
            if (entity == null) {
                continue;
            }
            boolean quoted = entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true);
            String schema = entity.getAnnotationMetadata().stringValue(MappedEntity.class, "schema")
                .filter(value -> !value.isEmpty()).map(value -> normalize(value, quoted)).orElse(null);
            if (schema == null) {
                schema = connection.getSchema();
            }
            if (schema == null || schema.isEmpty()) {
                throw new SQLException("Cannot determine the schema for PostgreSQL notification table " + entity.getPersistedName());
            }
            String channel = method.stringValue(PostgresChangeNotification.class, "channel").orElseThrow();
            List<String> columns = List.of(method.stringValues(PostgresChangeListenerQuery.class, "columns"));
            if (columns.isEmpty()) {
                throw new IllegalStateException("Missing generated PostgreSQL notification keys for " + method.getDescription(true));
            }
            Subscription subscription = new Subscription(schema, normalize(entity.getPersistedName(), quoted), channel);
            List<String> previous = subscriptions.putIfAbsent(subscription, columns);
            if (previous != null && !Set.copyOf(previous).equals(Set.copyOf(columns))) {
                throw new IllegalStateException("Conflicting PostgreSQL notification keys for " + subscription);
            }
        }
        return subscriptions;
    }

    private String normalize(String identifier, boolean quoted) {
        return PostgresNotificationIdentifiers.resolve(identifier, quoted, environment);
    }

    private static void createTrigger(Connection connection, String table, String name, String operations,
                                      String level, String function, String arguments) throws SQLException {
        execute(connection, "DROP TRIGGER IF EXISTS " + quote(name) + " ON " + table);
        execute(connection, "CREATE TRIGGER " + quote(name) + " AFTER " + operations + " ON " + table
            + " FOR EACH " + level + " EXECUTE FUNCTION " + function + "(" + arguments + ")");
    }

    private static String readFunction() {
        try (InputStream input = PostgresNotificationSchemaGenerator.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing PostgreSQL notification SQL resource " + RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read PostgreSQL notification SQL resource", e);
        }
    }

    private static String triggerName(Subscription subscription) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (subscription.schema() + "\0" + subscription.table() + "\0" + subscription.channel()).getBytes(StandardCharsets.UTF_8));
            return "mn_notify_" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String literal(String value) {
        return "E'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private record Subscription(String schema, String table, String channel) {
    }
}

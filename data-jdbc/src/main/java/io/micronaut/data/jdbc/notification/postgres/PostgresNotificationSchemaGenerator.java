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
 * Installs PostgreSQL notification functions and triggers during JDBC schema generation when opted in.
 * Supports {@link SchemaGenerate#CREATE} and {@link SchemaGenerate#CREATE_DROP}.
 *
 * <p>The configured notification mode selects either row-change notifications with primary keys and
 * invalidation on truncation, or table invalidations for insert, update, delete, and truncate statements.
 * The corresponding bundled SQL resource supplies the function definition.</p>
 *
 * <p>The shared listener registry supplies channels and generated key mappings without instantiating
 * listeners or repository operations during schema generation. Listeners for the same schema, table,
 * and channel share generated triggers. Installation errors fail application startup.</p>
 */
@Internal
@Singleton
@Requires(property = "micronaut.data.jdbc.notifications.postgres.schema-generate", value = "true")
public final class PostgresNotificationSchemaGenerator {

    private static final String SQL_RESOURCE_DIRECTORY = "/io/micronaut/data/jdbc/notification/postgres/";
    private static final String CHANGE_FUNCTION_RESOURCE = SQL_RESOURCE_DIRECTORY + "create-notification-function.sql";
    private static final String INVALIDATION_FUNCTION_RESOURCE = SQL_RESOURCE_DIRECTORY + "create-invalidation-function.sql";

    private static final String CHANGE_FUNCTION_NAME = "micronaut_data_notify_change";
    private static final String INVALIDATION_FUNCTION_NAME = "micronaut_data_notify_invalidation";

    private final ChangeListenerRegistry registry;
    private final Environment environment;
    private final boolean statementNotifications;

    /**
     * Creates a generator using the configured notification trigger mode.
     *
     * @param registry the discovered listener metadata grouped by datasource
     * @param environment the mapping placeholder resolver
     * @param configuration the notification schema-generation settings
     */
    public PostgresNotificationSchemaGenerator(ChangeListenerRegistry registry, Environment environment, PostgresNotificationConfiguration configuration) {
        this.registry = registry;
        this.environment = environment;
        this.statementNotifications = configuration.getSchemaGenerateMode() == PostgresNotificationConfiguration.SchemaGenerationMode.STATEMENT;
    }

    /**
     * Installs the selected function and triggers for subscribed PostgreSQL tables included in this
     * schema-generation pass. Does nothing for other dialects or schema-generation modes other than
     * {@link SchemaGenerate#CREATE} and {@link SchemaGenerate#CREATE_DROP}.
     *
     * <p>The function is installed once per subscribed schema in this pass. For each table/channel
     * subscription, all three generated trigger names are dropped before creating the selected mode's
     * triggers. This also replaces existing triggers under {@code CREATE} and allows mode changes
     * without leaving the previous mode's triggers active.</p>
     *
     * <p>Unrelated triggers and triggers for subscriptions absent from this pass are left unchanged.
     * Previously installed functions are not removed when switching modes. Functions and triggers
     * remain installed after application shutdown.</p>
     *
     * @param connection the schema-generation connection
     * @param configuration the datasource configuration
     * @param entities the entities included in this schema-generation pass
     * @throws SQLException if schema resolution or SQL execution fails
     * @throws IllegalStateException if listener key mappings are missing or conflicting, or the SQL resource cannot be read
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
        String functionName = statementNotifications ? INVALIDATION_FUNCTION_NAME : CHANGE_FUNCTION_NAME;
        String function = readFunction(statementNotifications ? INVALIDATION_FUNCTION_RESOURCE : CHANGE_FUNCTION_RESOURCE);
        Set<String> installedSchemas = new LinkedHashSet<>();
        for (Map.Entry<Subscription, List<String>> entry : subscriptions.entrySet()) {
            Subscription subscription = entry.getKey();
            String qualifiedFunction = quote(subscription.schema()) + "." + quote(functionName);
            if (installedSchemas.add(subscription.schema())) {
                execute(connection, function.replace("CREATE OR REPLACE FUNCTION " + functionName + "()",
                    "CREATE OR REPLACE FUNCTION " + qualifiedFunction + "()"));
            }
            String table = quote(subscription.schema()) + "." + quote(subscription.table());
            String name = triggerName(subscription);
            dropTrigger(connection, table, name + "_rows");
            dropTrigger(connection, table, name + "_truncate");
            dropTrigger(connection, table, name + "_statement");
            if (statementNotifications) {
                createTrigger(connection, table, name + "_statement", "INSERT OR UPDATE OR DELETE OR TRUNCATE",
                    "STATEMENT", qualifiedFunction, literal(subscription.channel()));
            } else {
                String arguments = literal(subscription.channel()) + ", " + entry.getValue().stream()
                    .map(PostgresNotificationSchemaGenerator::literal).collect(Collectors.joining(", "));
                createTrigger(connection, table, name + "_rows", "INSERT OR UPDATE OR DELETE", "ROW", qualifiedFunction, arguments);
                createTrigger(connection, table, name + "_truncate", "TRUNCATE", "STATEMENT", qualifiedFunction, arguments);
            }
        }
    }

    /**
     * Groups matching listeners by resolved schema, table, and channel, requiring consistent key mappings.
     * Mappings without an explicit schema use the schema-generation connection's current schema.
     *
     * @param connection the connection used to resolve the default schema
     * @param datasource the datasource whose listeners are considered
     * @param entities the entity mappings included in this schema-generation pass
     * @return distinct subscriptions and their mapped primary-key column names
     * @throws SQLException if the default schema cannot be resolved
     * @throws IllegalStateException if generated key mappings are missing or conflicting
     */
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
        execute(connection, "CREATE TRIGGER " + quote(name) + " AFTER " + operations + " ON " + table
            + " FOR EACH " + level + " EXECUTE FUNCTION " + function + "(" + arguments + ")");
    }

    private static void dropTrigger(Connection connection, String table, String name) throws SQLException {
        execute(connection, "DROP TRIGGER IF EXISTS " + quote(name) + " ON " + table);
    }

    private static String readFunction(String resource) {
        try (InputStream input = PostgresNotificationSchemaGenerator.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing PostgreSQL notification SQL resource " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read PostgreSQL notification SQL resource", e);
        }
    }

    /**
     * Derives a stable trigger-name prefix from the subscription identity, independent of trigger mode.
     * Hashing keeps names short enough for PostgreSQL identifiers, including the trigger-specific suffix.
     *
     * @param subscription the schema, table, and channel identifying the subscription
     * @return the common prefix for the subscription's generated trigger names
     */
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

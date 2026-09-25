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
package io.micronaut.data.jdbc.notification.oracle;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.data.intercept.annotation.OracleChangeListenerQuery;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.inject.ExecutableMethod;
import oracle.jdbc.OracleConnection;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Builds the Oracle-specific runtime definition for a discovered change listener.
 *
 * <p>The factory resolves the entity's mapped Oracle table, reads the compile-time generated
 * {@code ROWID} reload query, copies the annotation's registration properties, adds the required
 * Oracle {@code ROWID} and timeout settings, derives the renewal policy, and builds the
 * registration query.</p>
 *
 * <p>It also performs defensive runtime validation so invalid Oracle listener configuration
 * produces an error that identifies the listener method before registration is attempted.</p>
 */
final class OracleChangeListenerDefinitionFactory {
    private final JdbcRepositoryOperations operations;

    OracleChangeListenerDefinitionFactory(JdbcRepositoryOperations operations) {
        this.operations = operations;
    }

    OracleChangeListenerDefinition create(ChangeListenerMethod listenerMethod) {
        ExecutableMethod<?, ?> method = listenerMethod.method();
        AnnotationValue<OracleChangeNotification> notification = Objects.requireNonNull(
            method.getAnnotation(OracleChangeNotification.class),
            () -> "@ChangeListener method [" + method.getDescription(true) + "] requires @OracleChangeNotification for an Oracle datasource"
        );
        Argument<?> entityArgument = listenerMethod.entityArgument();
        RuntimePersistentEntity<?> persistentEntity = operations.getEntity(entityArgument.getType());
        OracleTableIdentifier tableIdentifier = OracleTableIdentifier.parse(
            new SqlQueryBuilder(Dialect.ORACLE).getTableName(persistentEntity)
        );
        String reloadQuery = method.stringValue(OracleChangeListenerQuery.class)
            .orElseThrow(() -> invalidChangeListener(method, "is missing its generated Oracle ROWID reload query"));
        Properties properties = registrationProperties(notification, method);
        OracleChangeNotificationRenewalPolicy renewalPolicy = renewalPolicy(notification, properties, method);
        return new OracleChangeListenerDefinition(
            listenerMethod.beanDefinition(),
            method,
            tableIdentifier,
            registrationQuery(notification, method, tableIdentifier, properties),
            new OracleChangeListenerEntityLoader<>(operations, entityArgument.getType(), reloadQuery),
            properties,
            renewalPolicy
        );
    }

    private static OracleChangeNotificationRenewalPolicy renewalPolicy(AnnotationValue<OracleChangeNotification> notification,
                                                                       Properties properties,
                                                                       ExecutableMethod<?, ?> method) {
        int timeoutSeconds = notification.intValue("timeoutSeconds").orElse(3600);
        int leadTimeSeconds = notification.intValue("renewalLeadTimeSeconds").orElse(60);
        OracleChangeNotification.RenewalMode mode = notification
            .enumValue("renewal", OracleChangeNotification.RenewalMode.class)
            .orElse(OracleChangeNotification.RenewalMode.OVERLAPPING);
        if (timeoutSeconds <= 0) {
            throw invalidChangeListener(method, "requires timeoutSeconds to be greater than 0");
        }
        if (mode == OracleChangeNotification.RenewalMode.OVERLAPPING
            && (leadTimeSeconds <= 0 || leadTimeSeconds >= timeoutSeconds)) {
            throw invalidChangeListener(method,
                "requires renewalLeadTimeSeconds to be greater than 0 and less than timeoutSeconds");
        }
        OracleChangeNotificationRenewalPolicy renewalPolicy =
            new OracleChangeNotificationRenewalPolicy(timeoutSeconds, mode, leadTimeSeconds);
        properties.setProperty(OracleConnection.NTF_TIMEOUT, Integer.toString(renewalPolicy.serverTimeoutSeconds()));
        return renewalPolicy;
    }

    private static Properties registrationProperties(AnnotationValue<OracleChangeNotification> notification,
                                                     ExecutableMethod<?, ?> method) {
        Properties properties = new Properties();
        List<AnnotationValue<OracleChangeNotification.Property>> propertyValues = notification
            .getAnnotations("properties", OracleChangeNotification.Property.class);
        for (AnnotationValue<OracleChangeNotification.Property> property : propertyValues) {
            String name = property.stringValue("name").orElse("");
            if (name.isBlank()) {
                throw invalidChangeListener(method, "has an Oracle property with a blank name");
            }
            String value = property.stringValue("value").orElse("");
            if (OracleConnection.DCN_NOTIFY_CHANGELAG.equals(name) && !"0".equals(value.trim())) {
                throw invalidChangeListener(method, "requires " + OracleConnection.DCN_NOTIFY_CHANGELAG
                    + " to be 0 so row-level operation and ROWID details are available");
            }
            if (OracleConnection.NTF_TIMEOUT.equals(name)) {
                throw invalidChangeListener(method, "must configure Oracle registration timeout with timeoutSeconds");
            }
            properties.setProperty(name, value);
        }
        properties.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
        return properties;
    }

    private static String registrationQuery(AnnotationValue<OracleChangeNotification> notification,
                                            ExecutableMethod<?, ?> method,
                                            OracleTableIdentifier tableIdentifier,
                                            Properties properties) {
        boolean isObjectChange = !Boolean.parseBoolean(properties.getProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION));
        String select = notification.stringValue("select").orElse("*").trim();
        String where = notification.stringValue("where").orElse("").trim();
        if ((!select.equals("*") || !where.isEmpty()) && isObjectChange) {
            throw invalidChangeListener(method, "may specify Oracle select or where only when "
                + OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION + " is true");
        }
        if (select.isEmpty()) {
            throw invalidChangeListener(method, "must have a non-blank Oracle select value");
        }
        return "SELECT " + select + " FROM " + tableIdentifier.sqlName() + (where.isEmpty() ? "" : " WHERE " + where);
    }

    private static IllegalStateException invalidChangeListener(ExecutableMethod<?, ?> method, String message) {
        return new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + message);
    }
}

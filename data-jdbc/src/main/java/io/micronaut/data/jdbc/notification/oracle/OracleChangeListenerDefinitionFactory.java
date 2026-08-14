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
import io.micronaut.inject.ExecutableMethod;
import oracle.jdbc.OracleConnection;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Converts a discovered listener method into a validated Oracle notification definition.
 *
 * <p>The generic processor has already selected the datasource and resolved the persistent entity
 * argument. This factory consumes the compile-time generated Oracle ROWID query and applies the
 * Oracle registration configuration.</p>
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
        String tableName = operations.getEntity(entityArgument.getType()).getPersistedName();
        String reloadQuery = method.stringValue(OracleChangeListenerQuery.class)
            .orElseThrow(() -> invalidChangeListener(method, "is missing its generated Oracle ROWID reload query"));
        Properties properties = registrationProperties(notification, method);
        return new OracleChangeListenerDefinition(
            listenerMethod.beanDefinition(),
            method,
            tableName,
            registrationQuery(notification, method, tableName, properties),
            new OracleChangeListenerEntityLoader<>(operations, entityArgument.getType(), reloadQuery),
            properties
        );
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
            properties.setProperty(name, value);
        }
        properties.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
        return properties;
    }

    private static String registrationQuery(AnnotationValue<OracleChangeNotification> notification,
                                            ExecutableMethod<?, ?> method,
                                            String tableName,
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
        return "SELECT " + select + " FROM " + tableName + (where.isEmpty() ? "" : " WHERE " + where);
    }

    private static IllegalStateException invalidChangeListener(ExecutableMethod<?, ?> method, String message) {
        return new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + message);
    }
}

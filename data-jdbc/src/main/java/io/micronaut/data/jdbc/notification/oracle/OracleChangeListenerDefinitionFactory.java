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
import io.micronaut.data.intercept.annotation.ChangeListenerQuery;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import oracle.jdbc.OracleConnection;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Converts a discovered listener method into a validated Oracle notification definition.
 *
 * <p>The generic processor has already selected the datasource. This factory therefore focuses on
 * Oracle concerns: listener signature validation, generated ROWID reload-query metadata,
 * registration properties, and the SQL used to associate an Oracle CQN registration with a
 * table or query.</p>
 */
final class OracleChangeListenerDefinitionFactory {
    private final DefaultJdbcRepositoryOperations operations;

    OracleChangeListenerDefinitionFactory(DefaultJdbcRepositoryOperations operations) {
        this.operations = operations;
    }

    OracleChangeListenerDefinition create(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        AnnotationValue<ChangeListener> changeListener = Objects.requireNonNull(method.getAnnotation(ChangeListener.class));
        Argument<?>[] arguments = method.getArguments();
        if (arguments.length != 1) {
            throw invalidChangeListener(method, "must have exactly one entity argument");
        }
        String tableName = operations.getEntity(arguments[0].getType()).getPersistedName();
        String reloadQuery = method.stringValue(ChangeListenerQuery.class)
            .orElseThrow(() -> invalidChangeListener(method, "is missing its generated Oracle reload query"));
        Properties properties = registrationProperties(changeListener, method);
        return new OracleChangeListenerDefinition(
            beanDefinition,
            method,
            tableName,
            registrationQuery(changeListener, method, tableName, properties),
            new OracleChangeListenerEntityLoader<>(operations, arguments[0].getType(), reloadQuery),
            properties
        );
    }

    private static Properties registrationProperties(AnnotationValue<ChangeListener> changeListener,
                                                     ExecutableMethod<?, ?> method) {
        Properties properties = new Properties();
        List<AnnotationValue<ChangeListener.Property>> propertyValues = changeListener
            .getAnnotations("properties", ChangeListener.Property.class);
        for (AnnotationValue<ChangeListener.Property> property : propertyValues) {
            String name = property.stringValue("name").orElse("");
            if (name.isBlank()) {
                throw invalidChangeListener(method, "has a property with a blank name");
            }
            properties.setProperty(name, property.stringValue("value").orElse(""));
        }
        properties.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
        return properties;
    }

    private static String registrationQuery(AnnotationValue<ChangeListener> changeListener,
                                            ExecutableMethod<?, ?> method,
                                            String tableName,
                                            Properties properties) {
        boolean isObjectChange = !Boolean.parseBoolean(properties.getProperty(OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION));
        String select = changeListener.stringValue("select").orElse("*").trim();
        String where = changeListener.stringValue("where").orElse("").trim();
        if ((!select.equals("*") || !where.isEmpty()) && isObjectChange) {
            throw invalidChangeListener(method, "may specify select or where only when " + OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION + " is true");
        }
        if (select.isEmpty()) {
            throw invalidChangeListener(method, "must have a non-blank select value");
        }
        return "SELECT " + select + " FROM " + tableName + (where.isEmpty() ? "" : " WHERE " + where);
    }

    private static IllegalStateException invalidChangeListener(ExecutableMethod<?, ?> method, String message) {
        return new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + message);
    }
}

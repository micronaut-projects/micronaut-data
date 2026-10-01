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

import io.micronaut.inject.ExecutableMethod;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

/**
 * Validates listener and datasource DCN registration options against the supported callback model.
 */
final class OracleChangeNotificationOptionsValidator {

    private OracleChangeNotificationOptionsValidator() {
    }

    /**
     * Validates annotation properties before framework defaults are applied.
     *
     * @param properties the explicitly requested listener properties
     * @param method the listener method used in diagnostics
     */
    static void validateListenerOptions(Properties properties, ExecutableMethod<?, ?> method) {
        for (String name : properties.stringPropertyNames()) {
            String error = invalidOption(name, properties.getProperty(name), true);
            if (error != null) {
                throw new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + error);
            }
        }
    }

    /**
     * Validates connection-level overrides before the JDBC driver applies them to a registration.
     *
     * @param connectionProperties the datasource-level DCN options
     * @param definition the listener definition and its requested registration options
     * @param dataSourceName the datasource name used in diagnostics
     */
    static void validateConnectionOptions(Properties connectionProperties,
                                          OracleChangeListenerDefinition definition,
                                          String dataSourceName) {
        for (String name : connectionProperties.stringPropertyNames()) {
            String error = invalidOption(name, connectionProperties.getProperty(name), false);
            if (error != null) {
                throw invalidRegistrationProperty(definition, dataSourceName, error);
            }
        }
        validateBooleanProperty(definition, connectionProperties, dataSourceName, OracleConnection.DCN_NOTIFY_ROWIDS, true);
        validateBooleanProperty(definition, connectionProperties, dataSourceName, OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, false);
        validateBooleanProperty(definition, connectionProperties, dataSourceName, OracleConnection.NTF_QOS_PURGE_ON_NTFN, false);
        validateIntegerProperty(definition, connectionProperties, dataSourceName, OracleConnection.DCN_NOTIFY_CHANGELAG, 0);
        validateIntegerProperty(definition, connectionProperties, dataSourceName, OracleConnection.NTF_TIMEOUT, 0);
    }

    private static @Nullable String invalidOption(String name, String value, boolean listenerOption) {
        if (name.isBlank()) {
            return "has an Oracle property with a blank name";
        }
        if (OracleConnection.DCN_CLIENT_INIT_REGID.equals(name)) {
            return name + ": reusing an existing reliable DCN registration is not supported";
        }
        if (OracleConnection.DCN_NOTIFY_ROWIDS.equals(name) && !"true".equalsIgnoreCase(value)) {
            return "requires " + name + " to be true so row-level operation and ROWID details are available";
        }
        if (OracleConnection.DCN_NOTIFY_CHANGELAG.equals(name) && !"0".equals(value.trim())) {
            return "requires " + name + " to be 0 so row-level operation and ROWID details are available";
        }
        if (OracleConnection.NTF_GROUPING_CLASS.equals(name)) {
            if (!OracleConnection.NTF_GROUPING_CLASS_NONE.equals(value)) {
                return name + ": notification grouping is not supported";
            }
        } else if (OracleConnection.NTF_GROUPING_VALUE.equals(name)
            || OracleConnection.NTF_GROUPING_TYPE.equals(name)
            || OracleConnection.NTF_GROUPING_REPEAT_TIME.equals(name)
            || OracleConnection.NTF_GROUPING_START_TIME.equals(name)) {
            return name + ": notification grouping is not supported";
        }
        if (OracleConnection.DCN_PULL_NOTIFICATIONS.equals(name) && !"false".equalsIgnoreCase(value)) {
            return name + " [" + value + "] is not supported because AQ pull delivery does not invoke the listener callback";
        }
        if (OracleConnection.DCN_PULL_QUEUE_NAME.equals(name)) {
            return name + " is not supported because AQ pull delivery does not invoke the listener callback";
        }
        if (listenerOption && OracleConnection.NTF_TIMEOUT.equals(name)) {
            return "must configure Oracle registration timeout with timeoutSeconds";
        }
        return null;
    }

    private static void validateBooleanProperty(OracleChangeListenerDefinition definition,
                                                Properties connectionProperties,
                                                String dataSourceName,
                                                String name,
                                                boolean defaultValue) {
        String override = connectionProperties.getProperty(name);
        if (override == null) {
            return;
        }
        boolean requested = Boolean.parseBoolean(definition.registrationProperties().getProperty(name, Boolean.toString(defaultValue)));
        boolean effective = Boolean.parseBoolean(override);
        if (requested != effective) {
            throw invalidRegistrationProperty(definition, dataSourceName, "effective " + name + " [" + effective
                + "] conflicts with listener setting [" + requested + "]");
        }
    }

    private static void validateIntegerProperty(OracleChangeListenerDefinition definition,
                                                Properties connectionProperties,
                                                String dataSourceName,
                                                String name,
                                                int defaultValue) {
        String override = connectionProperties.getProperty(name);
        if (override == null) {
            return;
        }
        String requested = definition.registrationProperties().getProperty(name, Integer.toString(defaultValue));
        try {
            if (Integer.parseInt(requested) != Integer.parseInt(override)) {
                throw invalidRegistrationProperty(definition, dataSourceName, "effective " + name + " [" + override
                    + "] conflicts with listener setting [" + requested + "]");
            }
        } catch (NumberFormatException e) {
            throw invalidRegistrationProperty(definition, dataSourceName, "effective " + name + " [" + override + "] is not an integer");
        }
    }

    private static IllegalStateException invalidRegistrationProperty(OracleChangeListenerDefinition definition,
                                                                     String dataSourceName,
                                                                     String message) {
        return new IllegalStateException("DCN registration for datasource [" + dataSourceName + "] and listener method ["
            + definition.method().getDescription(true) + "]: " + message);
    }
}

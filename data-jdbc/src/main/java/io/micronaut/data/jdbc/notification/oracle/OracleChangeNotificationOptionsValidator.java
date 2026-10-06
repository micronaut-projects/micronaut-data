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

import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

/**
 * Validates Oracle Database Change Notification options against the registration behavior
 * supported by Micronaut Data.
 *
 * <p>Annotation options are checked before defaults are added. Datasource options are checked only
 * when explicitly configured, while options reported by the JDBC registration are checked as the
 * effective values, including Oracle defaults for omitted framework-controlled settings.</p>
 */
final class OracleChangeNotificationOptionsValidator {

    private OracleChangeNotificationOptionsValidator() {
    }

    /**
     * Validates the datasource's {@code oracle.jdbc.dcnOptions} before registration. Unsupported
     * settings, including {@code NTF_TIMEOUT}, are rejected, and explicitly configured values for
     * other framework-controlled options must agree with the listener definition. An omitted option
     * is not treated as an override; its effective value is checked later using the options returned
     * by the JDBC registration.
     *
     * @param connectionProperties the datasource-level DCN options
     * @param definition           the listener definition and its requested registration options
     * @param dataSourceName       the datasource name used in diagnostics
     */
    static void validateConnectionOptions(Properties connectionProperties,
                                          OracleChangeListenerDefinition definition,
                                          String dataSourceName) {
        validateOptions(connectionProperties, definition, dataSourceName, false);
    }

    /**
     * Validates the effective registration options returned by the JDBC driver after it has
     * applied datasource-level options. Unsupported settings are rejected, and controlled
     * settings must agree with the listener definition. If a controlled option is absent from the
     * returned properties, its Oracle default is treated as the effective value.
     *
     * @param registrationOptions the options reported by the created registration
     * @param definition          the listener definition and its requested registration options
     * @param dataSourceName      the datasource name used in diagnostics
     */
    static void validateEffectiveOptions(Properties registrationOptions,
                                         OracleChangeListenerDefinition definition,
                                         String dataSourceName) {
        validateOptions(registrationOptions, definition, dataSourceName, true);
    }

    /**
     * Applies the shared option restrictions and verifies framework-controlled settings.
     *
     * @param options                the option set being validated
     * @param definition             the listener's requested registration settings
     * @param dataSourceName         the datasource name used in diagnostics
     * @param validateOmittedOptions whether absent controlled options should be checked using
     *                               their Oracle defaults
     */
    private static void validateOptions(Properties options,
                                        OracleChangeListenerDefinition definition,
                                        String dataSourceName,
                                        boolean validateOmittedOptions) {
        for (String name : options.stringPropertyNames()) {
            String error = invalidOption(name, options.getProperty(name), !validateOmittedOptions);
            if (error != null) {
                throw invalidRegistrationProperty(definition, dataSourceName, error);
            }
        }
        validateBooleanProperty(definition, options, dataSourceName, OracleConnection.DCN_NOTIFY_ROWIDS, true, validateOmittedOptions);
        validateBooleanProperty(definition, options, dataSourceName, OracleConnection.DCN_QUERY_CHANGE_NOTIFICATION, false, validateOmittedOptions);
        validateBooleanProperty(definition, options, dataSourceName, OracleConnection.NTF_QOS_PURGE_ON_NTFN, false, validateOmittedOptions);
        validateIntegerProperty(definition, options, dataSourceName, OracleConnection.DCN_NOTIFY_CHANGELAG, 0, validateOmittedOptions);
        validateIntegerProperty(definition, options, dataSourceName, OracleConnection.NTF_TIMEOUT, 0, validateOmittedOptions);
    }

    /**
     * Returns an error when an individual option is not supported for its configuration source.
     *
     * @param name           the option name
     * @param value          the configured option value
     * @param userConfiguredOption whether the option came from the listener annotation or datasource
     * @return the validation error, or {@code null} when the option is allowed
     */
    private static @Nullable String invalidOption(String name, String value, boolean userConfiguredOption) {
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
        if (userConfiguredOption && OracleConnection.NTF_TIMEOUT.equals(name)) {
            return name + ": registration timeouts are not supported for application-lifetime listeners";
        }
        return null;
    }

    /**
     * Checks that an effective boolean setting agrees with the listener's requested value.
     *
     * @param definition             the listener's requested registration settings
     * @param options                the option set being validated
     * @param dataSourceName         the datasource name used in diagnostics
     * @param name                   the boolean option name
     * @param defaultValue           the Oracle default used when the option is absent
     * @param validateOmittedOptions whether an absent option should be checked as its default
     */
    private static void validateBooleanProperty(OracleChangeListenerDefinition definition,
                                                Properties options,
                                                String dataSourceName,
                                                String name,
                                                boolean defaultValue,
                                                boolean validateOmittedOptions) {
        String actual = options.getProperty(name);
        if (actual == null && !validateOmittedOptions) {
            return;
        }
        boolean requested = Boolean.parseBoolean(definition.registrationProperties().getProperty(name, Boolean.toString(defaultValue)));
        boolean effective = Boolean.parseBoolean(actual == null ? Boolean.toString(defaultValue) : actual);
        if (requested != effective) {
            throw invalidRegistrationProperty(definition, dataSourceName, "effective " + name + " [" + effective
                + "] conflicts with listener setting [" + requested + "]");
        }
    }

    /**
     * Checks that an effective integer setting agrees with the listener's requested value.
     *
     * @param definition             the listener's requested registration settings
     * @param options                the option set being validated
     * @param dataSourceName         the datasource name used in diagnostics
     * @param name                   the integer option name
     * @param defaultValue           the Oracle default used when the option is absent
     * @param validateOmittedOptions whether an absent option should be checked as its default
     */
    private static void validateIntegerProperty(OracleChangeListenerDefinition definition,
                                                Properties options,
                                                String dataSourceName,
                                                String name,
                                                int defaultValue,
                                                boolean validateOmittedOptions) {
        String actual = options.getProperty(name);
        if (actual == null && !validateOmittedOptions) {
            return;
        }
        String requested = definition.registrationProperties().getProperty(name, Integer.toString(defaultValue));
        String effective = actual == null ? Integer.toString(defaultValue) : actual;
        try {
            if (Integer.parseInt(requested) != Integer.parseInt(effective)) {
                throw invalidRegistrationProperty(definition, dataSourceName, "effective " + name + " [" + effective
                    + "] conflicts with listener setting [" + requested + "]");
            }
        } catch (NumberFormatException e) {
            throw invalidRegistrationProperty(definition, dataSourceName, "effective " + name + " [" + effective + "] is not an integer");
        }
    }

    /**
     * Creates a registration-option error with datasource and listener context.
     *
     * @param definition     the listener definition used in diagnostics
     * @param dataSourceName the datasource name
     * @param message        the specific validation failure
     * @return the contextual exception
     */
    private static IllegalStateException invalidRegistrationProperty(OracleChangeListenerDefinition definition,
                                                                     String dataSourceName,
                                                                     String message) {
        return new IllegalStateException("DCN registration for datasource [" + dataSourceName + "] and listener method ["
            + definition.method().getDescription(true) + "]: " + message);
    }
}

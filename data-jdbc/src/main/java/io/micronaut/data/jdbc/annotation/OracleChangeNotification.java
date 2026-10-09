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
package io.micronaut.data.jdbc.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures Continuous Query Notification for a method annotated with {@link ChangeListener}.
 *
 * <p>For an Oracle datasource, every change-listener method must use this annotation, including
 * listeners that rely on its defaults. The annotation enables compile-time generation of a
 * {@code ROWID} reload query. Registrations always request row identifiers so inserts and updates
 * can be reloaded.
 * Oracle Database may report a full-table invalidation when row-level details are not
 * available, or report a dependent table for Query Result Change Notification. Either condition is
 * delivered as {@link io.micronaut.data.jdbc.notification.ChangeOperation#INVALIDATE}.</p>
 *
 * <p>Registrations do not expire by default. A positive {@code NTF_TIMEOUT} property makes Oracle
 * Database expire the registration after the specified number of seconds; Micronaut Data does not
 * renew it. When Oracle Database reports a deregistration, that listener becomes
 * unavailable. If the JDBC driver reports a notification-connection failure or Oracle Database
 * reports a shutdown, Micronaut Data attempts to create a replacement registration and dispatches
 * an invalidation event after recovery succeeds. Registrations are also unregistered during
 * application shutdown.</p>
 *
 * <p>Oracle JDBC client-initiated notification connections are enabled by default. Set the
 * {@code DCN_CLIENT_INIT_CONNECTION} registration property to {@code false} to use
 * server-initiated notification connections instead.</p>
 *
 * @since 5.3.0
 */
@Documented
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OracleChangeNotification {

    /**
     * The select list to register for Query Result Change Notification. The value must be {@code *}
     * or a comma-separated list of mapped column identifiers. Quoted identifiers must match the
     * Oracle-rendered column name exactly. The selection controls the result registered with Oracle
     * Database; it does not define a projection for the entity supplied to the listener. A
     * non-default selection is valid only when
     * {@link oracle.jdbc.OracleConnection#DCN_QUERY_CHANGE_NOTIFICATION} is enabled in {@link #properties()}.
     *
     * @return The mapped column list, or {@code *} to select all columns.
     */
    String select() default "*";

    /**
     * The predicate to register for Query Result Change Notification. A non-empty predicate is
     * valid only when {@link oracle.jdbc.OracleConnection#DCN_QUERY_CHANGE_NOTIFICATION} is enabled
     * in {@link #properties()}.
     *
     * @return The predicate, or an empty string to omit the {@code WHERE} clause.
     */
    String where() default "";

    /**
     * Oracle JDBC Continuous Query Notification registration properties for this listener.
     *
     * <p>The {@code DCN_CLIENT_INIT_CONNECTION} property defaults to {@code true} unless explicitly
     * configured here. The {@code NTF_TIMEOUT} property defaults to {@code 0}, which leaves the
     * registration without a configured expiry. Connection-level {@code oracle.jdbc.dcnOptions}
     * may override these values; conflicting overrides of framework-controlled settings fail
     * registration. Reattaching to an existing registration with {@code DCN_CLIENT_INIT_REGID} is
     * not supported.</p>
     *
     * @return The configured registration properties.
     */
    Property[] properties() default {};

    /**
     * An Oracle JDBC Continuous Query Notification registration property.
     */
    @Experimental
    @interface Property {

        /**
         * Gets the Oracle JDBC registration property name.
         *
         * @return The property name.
         */
        String name();

        /**
         * Gets the Oracle JDBC registration property value.
         *
         * @return The property value.
         */
        String value();
    }

}

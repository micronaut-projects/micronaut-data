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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures Continuous Query Notification for a method annotated with {@link ChangeListener}.
 *
 * <p>This annotation is required for change listeners, even when all members use their defaults.
 * It enables compile-time generation of the {@code ROWID} reload query. Row IDs are always
 * requested because they identify the affected row and allow inserts and updates to be reloaded.
 * Oracle Database may instead report a full-table invalidation when row-level details are not
 * available, or report a dependent table for Query Result Change Notification; either is delivered with
 * {@link io.micronaut.data.jdbc.notification.ChangeOperation#INVALIDATE}.</p>
 *
 * <p>Registrations have a finite lifetime. By default, Micronaut Data renews long-lived
 * registrations by activating a replacement shortly before the previous registration expires.
 * This avoids a planned delivery gap, but both registrations can briefly report the same database
 * change.</p>
 *
 * @since 5.2.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OracleChangeNotification {

    /**
     * Controls the finite lifetime of each registration.
     *
     * @return The number of seconds after which Oracle Database expires the registration.
     * Must be greater than zero.
     */
    int timeoutSeconds() default 3600;

    /**
     * Controls when Micronaut Data creates a replacement registration.
     *
     * @return The strategy used to replace an expiring registration
     */
    RenewalMode renewal() default RenewalMode.OVERLAPPING;

    /**
     * The lead time is used only by {@link RenewalMode#OVERLAPPING}.
     *
     * @return How many seconds before expiration the replacement is created. For overlapping
     * renewal, this must be greater than zero and less than {@link #timeoutSeconds()}.
     */
    int renewalLeadTimeSeconds() default 60;

    /**
     * The select list to register for Query Result Change Notification. The value must be {@code *}
     * or a comma-separated list of mapped column names. It controls the result registered
     * with Oracle Database and is not used as a projection for the entity supplied to the listener.
     * It is valid only when {@link oracle.jdbc.OracleConnection#DCN_QUERY_CHANGE_NOTIFICATION} is
     * enabled in {@link #properties()}.
     *
     * @return The mapped column list, or {@code *} to select all columns.
     */
    String select() default "*";

    /**
     * The predicate to register for Query Result Change Notification. It is valid only
     * when {@link oracle.jdbc.OracleConnection#DCN_QUERY_CHANGE_NOTIFICATION} is enabled in
     * {@link #properties()}.
     *
     * @return The predicate, or an empty string to omit the {@code WHERE} clause.
     */
    String where() default "";

    /**
     * @return Oracle JDBC Continuous Query Notification registration properties.
     */
    Property[] properties() default {};

    /**
     * An Oracle JDBC Continuous Query Notification registration property.
     */
    @interface Property {

        /**
         * @return The Oracle JDBC registration property name.
         */
        String name();

        /**
         * @return The Oracle JDBC registration property value.
         */
        String value();
    }

    /**
     * Determines whether successive registrations overlap.
     */
    enum RenewalMode {
        /**
         * Activates the replacement before unregistering the previous registration. This avoids
         * a planned renewal gap but can deliver the same change through both registrations.
         */
        OVERLAPPING,
        /**
         * Waits for Oracle Database to report that the previous registration expired before activating
         * its replacement. This avoids renewal overlap, but renewal depends on receiving the timeout
         * deregistration event and database changes can be missed while the replacement is created.
         */
        AFTER_EXPIRATION
    }
}

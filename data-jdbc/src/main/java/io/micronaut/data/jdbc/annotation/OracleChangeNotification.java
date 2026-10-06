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
 * <p>Registrations remain active for the application lifetime unless Oracle Database deregisters
 * them or the notification connection fails. Micronaut Data unregisters registrations during
 * application shutdown and attempts to recover from a reported notification connection failure.</p>
 *
 * <p>Oracle JDBC client-initiated notification connections are enabled by default. Set the
 * {@code DCN_CLIENT_INIT_CONNECTION} registration property to {@code false} to use
 * server-initiated notification connections instead.</p>
 *
 * @since 5.3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OracleChangeNotification {

    /**
     * The select list to register for Query Result Change Notification. The value must be {@code *}
     * or a comma-separated list of mapped column identifiers. Quoted identifiers must match the
     * Oracle-rendered column name exactly. It controls the result registered
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
     * @return Oracle JDBC Continuous Query Notification registration properties. The
     * {@code DCN_CLIENT_INIT_CONNECTION} property defaults to {@code true} unless explicitly
     * configured here. Connection-level {@code oracle.jdbc.dcnOptions} may override these values;
     * conflicting overrides of framework-controlled settings fail registration. Reattaching to
     * an existing registration with {@code DCN_CLIENT_INIT_REGID} is not supported.
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

}

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
 * Configures Oracle Continuous Query Notification for a method annotated with
 * {@link ChangeListener}.
 *
 * <p>This annotation is required for Oracle change listeners, even when all members use their
 * defaults. It enables compile-time generation of the Oracle {@code ROWID} reload query. Row IDs
 * are always requested because they identify the affected row and allow inserts and updates to be
 * reloaded.</p>
 *
 * @since 5.2.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OracleChangeNotification {

    /**
     * The select list to register for Oracle Query Result Change Notification. It is valid only
     * when {@link oracle.jdbc.OracleConnection#DCN_QUERY_CHANGE_NOTIFICATION} is enabled in
     * {@link #properties()}.
     *
     * @return The select list, or {@code *} to select all columns.
     */
    String select() default "*";

    /**
     * The predicate to register for Oracle Query Result Change Notification. It is valid only
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
}

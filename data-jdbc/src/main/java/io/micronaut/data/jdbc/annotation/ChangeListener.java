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

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Executable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that receives entities changed in an Oracle database.
 *
 * <p>The method must accept exactly one {@code @MappedEntity} argument. Oracle Continuous
 * Query Notification is registered for that entity's table and the listener receives each
 * inserted or updated row after it has been reloaded. Deleted rows cannot be reloaded and do
 * not invoke the listener. The datasource user must have Oracle's {@code CHANGE NOTIFICATION}
 * privilege.</p>
 *
 * @since 5.2.0
 */
@Documented
@Executable(processOnStartup = true)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ChangeListener {

    /**
     * @return The datasource that supplies the Oracle connection.
     */
    @AliasFor(member = "dataSource")
    String value() default "default";

    /**
     * @return The datasource that supplies the Oracle connection.
     */
    @AliasFor(member = "value")
    String dataSource() default "default";

    /**
     * The select list to register for Oracle Query Result Change Notification. It is valid only
     * when {@link oracle.jdbc.OracleConnection#DCN_QUERY_CHANGE_NOTIFICATION} is set to
     * {@code true} in {@link #properties()}.
     *
     * @return The select list, or {@code *} to select all columns.
     */
    String select() default "*";

    /**
     * The predicate to register for Oracle Query Result Change Notification. It is valid only
     * when {@link oracle.jdbc.OracleConnection#DCN_QUERY_CHANGE_NOTIFICATION} is set to
     * {@code true} in {@link #properties()}.
     *
     * @return The predicate, or an empty string to omit the {@code WHERE} clause.
     */
    String where() default "";

    /**
     * Oracle Continuous Query Notification registration properties. Row IDs are always enabled
     * because they are required to reload the changed entity. For example, use
     * {@code @Property(name = OracleConnection.DCN_CLIENT_INIT_CONNECTION, value = "true")} to
     * enable client-initiated notification delivery.
     *
     * @return The Oracle registration properties.
     */
    Property[] properties() default {};

    /**
     * An Oracle Continuous Query Notification registration property.
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

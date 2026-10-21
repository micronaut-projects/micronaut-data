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
 * Declares the PostgreSQL notification channel for a method annotated with {@link ChangeListener}.
 *
 * <p>Use database migrations to install the notification function and triggers for the entity's
 * table. Configure the triggers with the channel declared by this annotation and the entity's
 * primary-key column names as stored in PostgreSQL's catalogs. For development and tests using
 * schema creation, {@code micronaut.data.jdbc.notifications.postgres.schema-generate=true}
 * installs the function and triggers alongside tables generated with {@code CREATE} or {@code CREATE_DROP}.</p>
 *
 * <p>Notifications are matched by channel and mapped table name. If the entity mapping specifies
 * a schema, that schema must also match. Without an explicit schema, tables with the same name
 * in different schemas can trigger the same listener when they publish to its channel. Entity
 * loading without an explicit schema follows the datasource's schema search path and can therefore
 * load from a different schema than the notification's source. Specify a schema or use separate channels to avoid
 * this ambiguity.</p>
 *
 * <p>Notifications describe table changes and include the operation and the old or new primary keys,
 * as appropriate for that operation. Inserted and updated entities are loaded by primary key before
 * the listener is invoked. The loaded entity reflects the database state at the time of loading,
 * which may include changes made after the notification was sent. If the row no longer exists,
 * the event contains no entity.</p>
 *
 * <p>PostgreSQL notifications use one dedicated, non-pooled connection per participating datasource, shared by
 * all listener methods using that datasource. Callbacks run on the blocking executor and may
 * execute concurrently for the same listener, without guaranteed execution or completion order.
 * After each successful reconnection, an {@code INVALIDATE} event is submitted so listeners can
 * reload data that may have changed while disconnected.</p>
 *
 * @since 5.3.0
 */
@Documented
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostgresChangeNotification {

    /**
     * The exact, case-sensitive channel passed to PostgreSQL's {@code pg_notify} function.
     * Supply the channel name without surrounding SQL identifier quotes. The name must not be blank
     * or contain a NUL character, and its UTF-8 encoding must be at most 63 bytes long.
     *
     * @return The notification channel.
     */
    String channel();
}

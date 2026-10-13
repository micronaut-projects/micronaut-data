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
 * <p>Database migrations must install the notification function and table triggers using the
 * same channel and the entity's mapped primary-key column names. The version 1 trigger protocol
 * carries operations and old/new keys; it does not carry complete entities or register SQL query
 * results. Entity reloading represents current database state, not a historical row snapshot.</p>
 *
 * <p>This annotation defines the PostgreSQL listener contract. The runtime PostgreSQL notification
 * provider is not yet implemented; declaring this annotation alone does not start a subscription.</p>
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
     * Supply the name without SQL identifier quotes. It must be non-blank, contain no NUL
     * characters, and occupy at most 63 bytes in UTF-8.
     *
     * @return The notification channel.
     * @since 5.3.0
     */
    String channel();
}

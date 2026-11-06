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
package io.micronaut.data.jdbc.notification.postgres;

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification;
import io.micronaut.data.model.query.builder.sql.postgres.PostgresIdentifiers;

import java.nio.charset.StandardCharsets;

/**
 * Resolves mapped identifiers consistently for notification installation and event matching.
 */
final class PostgresNotificationIdentifiers {
    private PostgresNotificationIdentifiers() {
    }

    /**
     * Resolves required property placeholders before applying PostgreSQL identifier rules.
     * Missing properties fail startup rather than producing an identifier that cannot match events.
     */
    static String resolve(String identifier, boolean quoted, Environment environment) {
        return PostgresIdentifiers.normalize(environment.getPlaceholderResolver().resolveRequiredPlaceholders(identifier), quoted);
    }

    /**
     * Resolves channel placeholders and validates the resulting name without changing its spelling.
     * Channels must be rejected rather than truncated so LISTEN and trigger payloads use the same name.
     *
     * @param notification the notification annotation containing the channel literal or placeholder expression
     * @param environment the required placeholder resolver
     * @return the resolved, validated channel
     * @throws IllegalArgumentException if the resolved channel is blank, contains NUL, or exceeds 63 UTF-8 bytes
     */
    static String resolveChannel(AnnotationValue<PostgresChangeNotification> notification, Environment environment) {
        // Discard metadata value mappers so a previous application context cannot resolve the channel.
        var raw = new AnnotationValue<PostgresChangeNotification>(notification.getAnnotationName(), notification.getValues());
        String channel = raw.stringValue("channel").orElse("");
        String resolved = environment.getPlaceholderResolver().resolveRequiredPlaceholders(channel);
        if (resolved.isBlank() || resolved.indexOf('\0') >= 0 || resolved.getBytes(StandardCharsets.UTF_8).length > 63) {
            throw new IllegalArgumentException("@PostgresChangeNotification channel must be non-blank, contain no NUL characters, "
                + "and occupy at most 63 UTF-8 bytes after resolving property placeholders");
        }
        return resolved;
    }
}

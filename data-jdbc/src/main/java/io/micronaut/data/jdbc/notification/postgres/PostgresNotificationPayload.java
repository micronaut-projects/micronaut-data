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

import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.notification.ChangeOperation;
import io.micronaut.json.JsonMapper;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a decoded trigger notification with a validated operation, table identity, and key maps.
 *
 * @param operation The validated table operation.
 * @param metadata The validated table identity and keys.
 */
record PostgresNotificationPayload(ChangeOperation operation, PostgresChangeEventMetadata metadata) {
    private static final String SUPPORTED_VERSION = "1";

    static PostgresNotificationPayload decode(JsonMapper mapper, String channel, String payload) throws IOException {
        Map<String, Object> object = mapper.readValue(payload, Argument.mapOf(String.class, Object.class));
        if (object == null || !(object.get("version") instanceof Number version) || !version.toString().equals(SUPPORTED_VERSION)) {
            throw new IllegalArgumentException("Unsupported PostgreSQL notification version");
        }
        var operation = ChangeOperation.valueOf(string(object.get("operation")));
        var oldKeys = keys(object.get("oldKeys"));
        var newKeys = keys(object.get("newKeys"));
        boolean valid = switch (operation) {
            case INSERT -> oldKeys.isEmpty() && !newKeys.isEmpty();
            case UPDATE -> !oldKeys.isEmpty() && oldKeys.keySet().equals(newKeys.keySet());
            case DELETE -> !oldKeys.isEmpty() && newKeys.isEmpty();
            case INVALIDATE -> oldKeys.isEmpty() && newKeys.isEmpty();
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid PostgreSQL notification keys for " + operation);
        }
        return new PostgresNotificationPayload(operation, new PostgresChangeEventMetadata(channel,
            string(object.get("schema")), string(object.get("table")), oldKeys, newKeys));
    }

    private static String string(@Nullable Object value) {
        if (!(value instanceof String string) || string.isEmpty()) {
            throw new IllegalArgumentException("Expected a non-empty notification identifier");
        }
        return string;
    }

    private static Map<String, String> keys(@Nullable Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a notification key object");
        }
        Map<String, String> keys = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (!(item instanceof String text)) {
                throw new IllegalArgumentException("Expected a string notification key value");
            }
            keys.put(string(key), text);
        });
        return keys;
    }
}

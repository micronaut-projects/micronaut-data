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
package io.micronaut.data.document.model.query.builder;

import io.micronaut.core.annotation.Internal;

import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the aggregation pipeline the {@link MongoQueryBuilder} assembles as the relaxed JSON MongoDB parses:
 * single-quoted strings, bare keys where possible and empty documents or arrays left out.
 *
 * @since 5.4.0
 */
@Internal
final class MongoPipelineJson {

    private MongoPipelineJson() {
    }

    static String toJsonString(Object obj) {
        StringBuilder sb = new StringBuilder();
        append(sb, obj);
        return sb.toString();
    }

    private static void appendMap(StringBuilder sb, Map<String, Object> map) {
        sb.append("{");
        for (Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Object> e = iterator.next();
            String key = e.getKey();
            Object value = e.getValue();
            if (!skipValue(value)) {
                if (shouldEscapeKey(key)) {
                    sb.append("'").append(key).append("'");
                } else {
                    sb.append(key);
                }
                sb.append(":");
                append(sb, value);
                if (iterator.hasNext()) {
                    sb.append(",");
                }
            }
        }
        sb.append("}");
    }

    private static boolean skipValue(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (obj instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
    }

    private static void appendArray(StringBuilder sb, Collection<?> collection) {
        sb.append("[");
        for (Iterator<?> iterator = collection.iterator(); iterator.hasNext(); ) {
            Object value = iterator.next();
            append(sb, value);
            if (iterator.hasNext()) {
                sb.append(",");
            }
        }
        sb.append("]");
    }

    private static void append(StringBuilder sb, Object obj) {
        if (obj instanceof Map map) {
            appendMap(sb, map);
        } else if (obj instanceof Collection<?> collection) {
            appendArray(sb, collection);
        } else if (obj instanceof RawJsonValue rawJsonValue) {
            sb.append(rawJsonValue.value);
        } else if (obj == null) {
            sb.append("null");
        } else if (obj instanceof Boolean) {
            sb.append(obj.toString().toLowerCase(Locale.ROOT));
        } else if (obj instanceof Number) {
            sb.append(obj);
        } else {
            appendString(sb, obj.toString());
        }
    }

    private static void appendString(StringBuilder sb, String value) {
        // The string is single-quoted, a backslash starts an escape sequence in the JSON parsed by MongoDB
        sb.append('\'').append(value.replace("\\", "\\\\").replace("'", "\\'")).append('\'');
    }

    private static boolean shouldEscapeKey(String s) {
        for (char c : s.toCharArray()) {
            if (!Character.isAlphabetic(c) && !Character.isDigit(c) && c != '$' && c != '_') {
                return true;
            }
        }
        return false;
    }

    /**
     * JSON the caller wrote, appended as it is.
     *
     * @param value The JSON
     */
    record RawJsonValue(String value) {
    }
}

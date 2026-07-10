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
package io.micronaut.data.nitrite.model.query.builder;

import java.util.Collection;
import java.util.Map;

/** Serializes query maps and values into the Nitrite JSON filter string format. */
final class NitriteQuerySerializer {

    private NitriteQuerySerializer() { }

    static String toJsonString(Object obj) {
        switch (obj) {
            case null -> {
                return "null";
            }
            case Map<?, ?> map -> {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof Collection<?> c && c.isEmpty()) {
                        continue;
                    }
                    if (!first) {
                        sb.append(",");
                    }
                    first = false;
                    String k = entry.getKey().toString();
                    sb.append(needsQuoting(k) ? "'" + k + "'" : k).append(":");
                    sb.append(toJsonString(val));
                }
                sb.append("}");
                return sb.toString();
            }
            case Collection<?> coll -> {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Object item : coll) {
                    if (!first) {
                        sb.append(",");
                    }
                    first = false;
                    sb.append(toJsonString(item));
                }
                sb.append("]");
                return sb.toString();
            }
            case String str -> {
                return "'" + str.replace("'", "\\'") + "'";
            }
            case Boolean b -> {
                return b.toString();
            }
            case Number ignored -> {
                return obj.toString();
            }
            default -> {
            }
        }
        return "'" + obj.toString().replace("'", "\\'") + "'";
    }

    private static boolean needsQuoting(String key) {
        for (char c : key.toCharArray()) {
            if (!Character.isAlphabetic(c) && !Character.isDigit(c) && c != '$' && c != '_') {
                return true;
            }
        }
        return false;
    }
}

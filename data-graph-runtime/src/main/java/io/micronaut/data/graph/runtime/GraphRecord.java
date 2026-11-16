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
package io.micronaut.data.graph.runtime;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A row returned by a graph query. Values are plain Java values, {@link GraphNode}, {@link GraphEdge},
 * {@link GraphPathValue}, or lists and maps of those.
 *
 * @param keys   The column names
 * @param values The values, in column order
 * @author graemerocher
 * @since 5.3.0
 */
public record GraphRecord(List<String> keys, List<@Nullable Object> values) {

    /**
     * @param index The column index
     * @return The value
     */
    public @Nullable Object get(int index) {
        return values.get(index);
    }

    /**
     * @param key The column name
     * @return The value, or null if the column is absent or null
     */
    public @Nullable Object get(String key) {
        int index = keys.indexOf(key);
        return index < 0 ? null : values.get(index);
    }

    /**
     * @param key The column name
     * @return Whether the row has the column
     */
    public boolean has(String key) {
        return keys.contains(key);
    }

    /**
     * @return The number of columns
     */
    public int size() {
        return values.size();
    }

    /**
     * @return The row as a map from column name to value
     */
    public Map<String, @Nullable Object> asMap() {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            map.put(keys.get(i), values.get(i));
        }
        return map;
    }
}

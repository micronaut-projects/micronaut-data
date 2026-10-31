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
package io.micronaut.data.search.document.runtime;

import io.micronaut.core.annotation.Internal;

import java.nio.charset.StandardCharsets;

/**
 * Checks index names against the Elasticsearch and OpenSearch naming rules before they go into a
 * request path, where {@code *} or {@code ,} would address several indices.
 *
 * @since 5.4.0
 */
@Internal
public final class SearchIndexNames {

    private static final String FORBIDDEN = "\\/*?\"<>| ,#:";
    private static final int MAX_BYTES = 255;

    private SearchIndexNames() {
    }

    /**
     * @param name The index name, prefix included
     * @return The name
     * @throws IllegalArgumentException if the name breaks the index naming rules
     */
    public static String validate(String name) {
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw invalid(name, "it is empty, . or ..");
        }
        char first = name.charAt(0);
        if (first == '-' || first == '_' || first == '+') {
            throw invalid(name, "it starts with -, _ or +");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (FORBIDDEN.indexOf(c) >= 0 || Character.isWhitespace(c)) {
                throw invalid(name, "it contains '" + c + "'");
            }
            if (Character.isUpperCase(c)) {
                throw invalid(name, "it contains an upper case letter");
            }
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid(name, "it is longer than 255 bytes");
        }
        return name;
    }

    private static IllegalArgumentException invalid(String name, String reason) {
        return new IllegalArgumentException("Invalid search index name '" + name + "': " + reason
            + ". Check micronaut.data.search.index-prefix and the entity's index name");
    }
}

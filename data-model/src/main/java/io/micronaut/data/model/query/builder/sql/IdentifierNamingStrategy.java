/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.util.StringUtils;

/**
 * An enumeration representing different strategies for naming identifiers.
 *
 * @author radovanradic
 * @since 4.13.0
 */
public enum IdentifierNamingStrategy {

    /**
     * Converts all characters to uppercase.
     */
    UPPER,

    /**
     * Converts all characters to lowercase.
     */
    LOWER,

    /**
     * Leaves the original casing unchanged.
     */
    MIXED;

    /**
     * Applies this strategy to the given string identifier.
     *
     * @param source the input string to transform
     * @return the transformed string according to this strategy
     */
    public String apply(String source) {
        if (StringUtils.isEmpty(source)) {
            return source;
        }
        return switch (this) {
            case UPPER -> source.toUpperCase();
            case LOWER -> source.toLowerCase();
            case MIXED -> source;
        };
    }
}

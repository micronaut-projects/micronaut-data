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
package io.micronaut.data.nitrite.model.query;

import io.micronaut.core.annotation.Internal;

/**
 * Marker keys written into a built query by the Nitrite query builder and consumed again when the
 * query is bound at runtime. Unlike {@link NitriteQueryOperators} these are not Nitrite operators:
 * they never reach the store and exist only to carry binding metadata between build and execution.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteInternalKeys {

    /** Query parameter placeholder key. */
    public static final String QUERY_PARAMETER_PLACEHOLDER = "$mn_qp";
    /** Query parameter placeholder string prefix. */
    public static final String QUERY_PARAMETER_PREFIX = QUERY_PARAMETER_PLACEHOLDER + ":";
    /** Parameterized LIKE pattern key. */
    public static final String LIKE_PATTERN = "$mn_like_pattern";
    /** Parameterized LIKE escape key. */
    public static final String LIKE_ESCAPE = "$mn_like_escape";
    /** Case-insensitive LIKE flag. */
    public static final String LIKE_IGNORE_CASE = "$mn_like_ignore_case";
    /** Parameterized regex pattern key. */
    public static final String REGEX_PATTERN = "$mn_regex_pattern";
    /** Parameterized starts-with regex flag. */
    public static final String REGEX_STARTS_WITH = "$mn_regex_starts_with";
    /** Parameterized ends-with regex flag. */
    public static final String REGEX_ENDS_WITH = "$mn_regex_ends_with";
    /** Case-insensitive parameterized regex flag. */
    public static final String REGEX_IGNORE_CASE = "$mn_regex_ignore_case";
    /** Numeric negation update flag. */
    public static final String NEGATE = "$mn_negate";
    /** Numeric reciprocal update flag. */
    public static final String RECIPROCATE = "$mn_reciprocate";

    private NitriteInternalKeys() {
    }
}

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
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Shared Nitrite JSON query operator names.
 *
 * @since 5.0.0
 */
@Internal
public final class NitriteQueryOperators {

    public static final String AND = "$and";
    public static final String OR = "$or";
    public static final String NOT = "$not";
    public static final String EXPR = "$expr";
    public static final String EXISTS = "$exists";
    public static final String EMPTY = "$empty";
    public static final String TEXT = "$text";
    public static final String ALL = "$all";

    public static final String EQ = "$eq";
    public static final String NE = "$ne";
    public static final String GT = "$gt";
    public static final String GTE = "$gte";
    public static final String LT = "$lt";
    public static final String LTE = "$lte";
    public static final String IN = "$in";
    public static final String NIN = "$nin";
    public static final String BETWEEN = "$between";
    public static final String REGEX = "$regex";
    public static final String LIKE = "$like";
    public static final String NULL = "$null";
    public static final String NOT_NULL = "$notNull";

    public static final String STR_LEN_CP = "$strLenCP";
    public static final String TO_LOWER = "$toLower";
    public static final String TO_UPPER = "$toUpper";
    public static final String MULTIPLY = "$multiply";
    public static final String CONCAT = "$concat";
    public static final String SUBSTR_CP = "$substrCP";
    public static final String RIGHT = "$right";
    public static final String DIVIDE = "$divide";
    public static final String TO_DOUBLE = "$toDouble";

    public static final String NEAR = "$near";
    public static final String WITHIN = "$within";
    public static final String INTERSECTS = "$intersects";

    private NitriteQueryOperators() {
    }

    public static Map<String, @Nullable Object> operator(String operator, @Nullable Object value) {
        return Collections.singletonMap(operator, value);
    }

    public static Map<String, Object> expression(String operator, List<?> operands) {
        return Map.of(EXPR, operator(operator, operands));
    }
}

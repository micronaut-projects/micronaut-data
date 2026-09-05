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
 * @since 5.2.0
 */
@Internal
public final class NitriteQueryOperators {

    /** Logical AND operator. */
    public static final String AND = "$and";
    /** Logical OR operator. */
    public static final String OR = "$or";
    /** Logical NOT operator. */
    public static final String NOT = "$not";
    /** Expression operator. */
    public static final String EXPR = "$expr";
    /** Exists operator. */
    public static final String EXISTS = "$exists";
    /** Empty operator. */
    public static final String EMPTY = "$empty";
    /** Text search operator. */
    public static final String TEXT = "$text";
    /** All operator for array matching. */
    public static final String ALL = "$all";

    /** Equals operator. */
    public static final String EQ = "$eq";
    /** Not-equals operator. */
    public static final String NE = "$ne";
    /** Greater-than operator. */
    public static final String GT = "$gt";
    /** Greater-than-or-equal operator. */
    public static final String GTE = "$gte";
    /** Less-than operator. */
    public static final String LT = "$lt";
    /** Less-than-or-equal operator. */
    public static final String LTE = "$lte";
    /** In operator. */
    public static final String IN = "$in";
    /** Not-in operator. */
    public static final String NIN = "$nin";
    /** Between operator. */
    public static final String BETWEEN = "$between";
    /** Regex operator. */
    public static final String REGEX = "$regex";
    /** Like operator. */
    public static final String LIKE = "$like";
    /** Null operator. */
    public static final String NULL = "$null";
    /** Not-null operator. */
    public static final String NOT_NULL = "$notNull";

    /** String length operator. */
    public static final String STR_LEN_CP = "$strLenCP";
    /** To-lower operator. */
    public static final String TO_LOWER = "$toLower";
    /** To-upper operator. */
    public static final String TO_UPPER = "$toUpper";
    /** Multiply operator. */
    public static final String MULTIPLY = "$multiply";
    /**
     * Concatenate operator. It appears in two distinct positions and the two are not
     * interchangeable: inside {@code $expr} its value is a {@code List} of operands to join, while
     * as a top-level update operator its value is a {@code Map} of field to the value appended to
     * that field. Consumers of either form must type-check the value before reading it.
     */
    public static final String CONCAT = "$concat";
    /** Substring operator. */
    public static final String SUBSTR_CP = "$substrCP";
    /** Right substring operator. */
    public static final String RIGHT = "$right";
    /** Divide operator. */
    public static final String DIVIDE = "$divide";
    /** To-double operator. */
    public static final String TO_DOUBLE = "$toDouble";

    /** Near geospatial operator. */
    public static final String NEAR = "$near";
    /** Within geospatial operator. */
    public static final String WITHIN = "$within";
    /** Intersects geospatial operator. */
    public static final String INTERSECTS = "$intersects";

    /** Match pipeline stage. */
    public static final String MATCH = "$match";
    /** Group pipeline stage. */
    public static final String GROUP = "$group";
    /** Sort pipeline stage. */
    public static final String SORT = "$sort";
    /** Projection pipeline stage. */
    public static final String PROJECT = "$project";
    /** Limit pipeline stage. */
    public static final String LIMIT = "$limit";
    /** Skip pipeline stage. */
    public static final String SKIP = "$skip";
    /** Count pipeline stage. */
    public static final String COUNT = "$count";
    /** Lookup pipeline stage. */
    public static final String LOOKUP = "$lookup";
    /** Unwind pipeline stage. */
    public static final String UNWIND = "$unwind";

    /** Sum aggregation operator. */
    public static final String SUM = "$sum";
    /** Average aggregation operator. */
    public static final String AVG = "$avg";
    /** Maximum aggregation operator. */
    public static final String MAX = "$max";
    /** Minimum aggregation operator. */
    public static final String MIN = "$min";

    /** Set update operator. */
    public static final String SET = "$set";
    /** Increment update operator. */
    public static final String INC = "$inc";
    /** Multiply update operator. */
    public static final String MUL = "$mul";
    /** Wrapped update value key. */
    public static final String VALUE = "$value";

    private NitriteQueryOperators() {
    }

    /**
     * Creates a single-operator query map entry.
     *
     * @param operator the operator name
     * @param value the value to match
     * @return a singleton map containing the operator and value
     */
    public static Map<String, @Nullable Object> operator(String operator, @Nullable Object value) {
        return Collections.singletonMap(operator, value);
    }

    /**
     * Creates an expression query wrapping the given operator and operands.
     *
     * @param operator the operator name
     * @param operands the list of operands
     * @return a map containing the {@code $expr} expression
     */
    public static Map<String, Object> expression(String operator, List<?> operands) {
        return Map.of(EXPR, operator(operator, operands));
    }
}

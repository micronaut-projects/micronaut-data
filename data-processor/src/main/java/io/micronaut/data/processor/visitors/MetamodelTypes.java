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
package io.micronaut.data.processor.visitors;

import java.util.Set;

import static io.micronaut.inject.ast.PrimitiveElement.BOOLEAN;
import static io.micronaut.inject.ast.PrimitiveElement.BYTE;
import static io.micronaut.inject.ast.PrimitiveElement.CHAR;
import static io.micronaut.inject.ast.PrimitiveElement.DOUBLE;
import static io.micronaut.inject.ast.PrimitiveElement.FLOAT;
import static io.micronaut.inject.ast.PrimitiveElement.INT;
import static io.micronaut.inject.ast.PrimitiveElement.LONG;
import static io.micronaut.inject.ast.PrimitiveElement.SHORT;

/**
 * Utility class containing commonly used Java type names .
 * <p>
 * This class groups Java types into categories such as:
 * <ul>
 *     <li>NumericAttribute</li>
 *     <li>TemporalAttribute</li>
 *     <li>TextAttribute</li>
 *     <li>BooleanAttribute</li>
 *     <li>BasicAttribute</li>
 * </ul>
 */
public final class MetamodelTypes {

    /**
     * {@link Byte} type name.
     */
    public static final String J_BYTE = "java.lang.Byte";

    /**
     * {@link Short} type name.
     */
    public static final String J_SHORT = "java.lang.Short";

    /**
     * {@link Integer} type name.
     */
    public static final String J_INTEGER = "java.lang.Integer";

    /**
     * {@link Long} type name.
     */
    public static final String J_LONG = "java.lang.Long";

    /**
     * {@link Float} type name.
     */
    public static final String J_FLOAT = "java.lang.Float";

    /**
     * {@link Double} type name.
     */
    public static final String J_DOUBLE = "java.lang.Double";

    /**
     * {@link java.math.BigInteger} type name.
     */
    public static final String J_BIG_INTEGER = "java.math.BigInteger";

    /**
     * {@link java.math.BigDecimal} type name.
     */
    public static final String J_BIG_DECIMAL = "java.math.BigDecimal";

    /**
     * {@link java.util.Date} type name.
     */
    public static final String J_UTIL_DATE = "java.util.Date";

    /**
     * {@link java.util.Calendar} type name.
     */
    public static final String J_UTIL_CALENDAR = "java.util.Calendar";

    /**
     * {@link java.util.GregorianCalendar} type name.
     */
    public static final String J_UTIL_GREGORIAN_CALENDAR = "java.util.GregorianCalendar";

    /**
     * {@link java.sql.Date} type name.
     */
    public static final String J_SQL_DATE = "java.sql.Date";

    /**
     * {@link java.sql.Time} type name.
     */
    public static final String J_SQL_TIME = "java.sql.Time";

    /**
     * {@link java.sql.Timestamp} type name.
     */
    public static final String J_SQL_TIMESTAMP = "java.sql.Timestamp";

    /**
     * {@link java.time.Instant} type name.
     */
    public static final String JT_INSTANT = "java.time.Instant";

    /**
     * {@link java.time.LocalDate} type name.
     */
    public static final String JT_LOCAL_DATE = "java.time.LocalDate";

    /**
     * {@link java.time.LocalTime} type name.
     */
    public static final String JT_LOCAL_TIME = "java.time.LocalTime";

    /**
     * {@link java.time.LocalDateTime} type name.
     */
    public static final String JT_LOCAL_DATE_TIME = "java.time.LocalDateTime";

    /**
     * {@link java.time.OffsetTime} type name.
     */
    public static final String JT_OFFSET_TIME = "java.time.OffsetTime";

    /**
     * {@link java.time.OffsetDateTime} type name.
     */
    public static final String JT_OFFSET_DATE_TIME = "java.time.OffsetDateTime";

    /**
     * {@link java.time.ZonedDateTime} type name.
     */
    public static final String JT_ZONED_DATE_TIME = "java.time.ZonedDateTime";

    /**
     * {@link java.time.Year} type name.
     */
    public static final String JT_YEAR = "java.time.Year";

    /**
     * {@link java.time.YearMonth} type name.
     */
    public static final String JT_YEAR_MONTH = "java.time.YearMonth";

    /**
     * {@link java.time.Month} type name.
     */
    public static final String JT_MONTH = "java.time.Month";

    /**
     * {@link java.time.MonthDay} type name.
     */
    public static final String JT_MONTH_DAY = "java.time.MonthDay";

    /**
     * {@link String} type name.
     */
    public static final String JL_STRING = "java.lang.String";

    /**
     * {@link Character} type name.
     */
    public static final String JL_CHARACTER = "java.lang.Character";

    /**
     * {@link CharSequence} type name.
     */
    public static final String CHAR_SEQUENCE = "java.lang.CharSequence";

    /**
     * {@link Boolean} type name.
     */
    public static final String JL_BOOLEAN = "java.lang.Boolean";

    /**
     * {@link java.util.UUID} type name.
     */
    public static final String J_UTIL_UUID = "java.util.UUID";

    /**
     * {@link jakarta.annotation.Generated} annotation name.
     */
    public static final String JAKARTA_ANNOTATION_GENERATED = "jakarta.annotation.Generated";

    /**
     * {@link jakarta.data.metamodel.StaticMetamodel} annotation name.
     */
    public static final String JAKARTA_DATA_STATIC_METAMODEL = "jakarta.data.metamodel.StaticMetamodel";

    /**
     * {@link jakarta.data.metamodel.BasicAttribute} type name.
     */
    public static final String JAKARTA_DATA_BASIC_ATTRIBUTE = "jakarta.data.metamodel.BasicAttribute";

    /**
     * {@link jakarta.data.metamodel.TextAttribute} type name.
     */
    public static final String JAKARTA_DATA_TEXT_ATTRIBUTE = "jakarta.data.metamodel.TextAttribute";

    /**
     * {@link jakarta.data.metamodel.NumericAttribute} type name.
     */
    public static final String JAKARTA_DATA_NUMERIC_ATTRIBUTE = "jakarta.data.metamodel.NumericAttribute";

    /**
     * {@link jakarta.data.metamodel.BooleanAttribute} type name.
     */
    public static final String JAKARTA_DATA_BOOLEAN_ATTRIBUTE = "jakarta.data.metamodel.BooleanAttribute";

    /**
     * {@link jakarta.data.metamodel.TemporalAttribute} type name.
     */
    public static final String JAKARTA_DATA_TEMPORAL_ATTRIBUTE = "jakarta.data.metamodel.TemporalAttribute";

    /**
     * {@link jakarta.data.metamodel.NavigableAttribute} type name.
     */
    public static final String JAKARTA_DATA_NAVIGABLE_ATTRIBUTE = "jakarta.data.metamodel.NavigableAttribute";

    /**
     * {@link jakarta.data.metamodel.SortableAttribute} type name.
     */
    public static final String JAKARTA_DATA_SORTABLE_ATTRIBUTE = "jakarta.data.metamodel.SortableAttribute";

    /**
     * {@link jakarta.data.metamodel.ComparableAttribute} type name.
     */
    public static final String JAKARTA_DATA_COMPARABLE_ATTRIBUTE = "jakarta.data.metamodel.ComparableAttribute";

    /**
     * Set of numeric Java types.
     */
    public static final Set<String> NUMERIC_TYPES = Set.of(
        INT.getName(),
        FLOAT.getName(),
        DOUBLE.getName(),
        BYTE.getName(),
        LONG.getName(),
        SHORT.getName(),
        J_BYTE,
        J_SHORT,
        J_INTEGER,
        J_LONG,
        J_FLOAT,
        J_DOUBLE,
        J_BIG_INTEGER,
        J_BIG_DECIMAL
    );

    /**
     * Set of temporal Java types.
     */
    public static final Set<String> TEMPORAL_TYPES = Set.of(
        J_UTIL_DATE,
        J_UTIL_CALENDAR,
        J_UTIL_GREGORIAN_CALENDAR,
        J_SQL_DATE,
        J_SQL_TIME,
        J_SQL_TIMESTAMP,
        JT_INSTANT,
        JT_LOCAL_DATE,
        JT_LOCAL_TIME,
        JT_LOCAL_DATE_TIME,
        JT_OFFSET_TIME,
        JT_OFFSET_DATE_TIME,
        JT_ZONED_DATE_TIME,
        JT_YEAR,
        JT_YEAR_MONTH,
        JT_MONTH,
        JT_MONTH_DAY
    );

    /**
     * Set of textual Java types.
     */
    public static final Set<String> TEXT_TYPES = Set.of(
        CHAR.getName(),
        JL_STRING,
        JL_CHARACTER,
        CHAR_SEQUENCE
    );

    /**
     * Set of boolean Java.
     */
    public static final Set<String> BOOLEAN_TYPES = Set.of(
        BOOLEAN.getName(),
        JL_BOOLEAN
    );

    /**
     * Determines whether the given type is considered numeric.
     *
     * @param type The fully qualified Java type name
     * @return {@code true} if the type is numeric
     */
    public static boolean isNumeric(String type) {
        return NUMERIC_TYPES.contains(type);
    }

    /**
     * Determines whether the given type is considered temporal.
     *
     * @param type The fully qualified Java type name
     * @return {@code true} if the type is temporal
     */
    public static boolean isTemporal(String type) {
        return TEMPORAL_TYPES.contains(type);
    }

    /**
     * Determines whether the given type is considered textual.
     *
     * @param type The fully qualified Java type name
     * @return {@code true} if the type is textual
     */
    public static boolean isText(String type) {
        return TEXT_TYPES.contains(type);
    }

    /**
     * Determines whether the given type is considered boolean.
     *
     * @param type The fully qualified Java type name
     * @return {@code true} if the type is boolean
     */
    public static boolean isBoolean(String type) {
        return BOOLEAN_TYPES.contains(type);
    }

    /**
     * Determines whether the given type is a collection type.
     *
     * @param type The fully qualified Java type name
     * @return {@code true} if the type is a collection type
     */
    public static boolean isUuid(String type) {
        return type.equals(J_UTIL_UUID);
    }

}

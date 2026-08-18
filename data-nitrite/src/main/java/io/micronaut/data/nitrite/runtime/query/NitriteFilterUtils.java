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
package io.micronaut.data.nitrite.runtime.query;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;

/**
 * Constructs the leaf Nitrite filters this module needs.
 *
 * <p>This is not a full abstraction over Nitrite's filter API and is not the single place every
 * filter is built: the combinators ({@code Filter.and}, {@code Filter.or}, {@code Filter.ALL},
 * {@code Filter.not}) are used directly at their call sites, and {@code NitriteFilterBuilder}
 * evaluates range comparisons itself rather than through the operators here. What this class does
 * own is the definition of null matching - see {@link #isNullFilter} - which otherwise gets
 * open-coded inconsistently; the remaining methods only shorten the call sites.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteFilterUtils {

    private NitriteFilterUtils() {
        // Utility class
    }

    /**
     * Creates a filter matching both an explicit null and a missing field.
     *
     * <p>A document that never carried the field is indistinguishable from one storing null: the
     * mapper omits a null-valued property rather than writing it. Both therefore have to match, and
     * this applies to an equality comparison against a null value too, not only to an explicit
     * {@code IS NULL} - so a bound parameter resolving to null matches documents lacking the field,
     * unlike SQL, where {@code x = NULL} matches nothing.
     *
     * @param field The field to check.
     * @return The constructed Filter.
     */
    public static Filter isNullFilter(String field) {
        return Filter.or(eq(field, null), exists(field).not());
    }

    /**
     * Creates a filter matching a field that is present and not null. The inverse of
     * {@link #isNullFilter}; a document lacking the field does not match.
     *
     * @param field The field to check.
     * @return The constructed Filter.
     */
    public static Filter isNotNullFilter(String field) {
        return Filter.and(notEq(field, null), exists(field));
    }

    /**
     * Creates an equality filter.
     *
     * @param field The field to check.
     * @param value The value to match.
     * @return The constructed Filter.
     */
    public static Filter eq(String field, @Nullable Object value) {
        return FluentFilter.where(field).eq(value);
    }

    /**
     * Creates a non-equality filter.
     *
     * @param field The field to check.
     * @param value The value to not match.
     * @return The constructed Filter.
     */
    public static Filter notEq(String field, @Nullable Object value) {
        return FluentFilter.where(field).notEq(value);
    }

    /**
     * Creates a greater-than filter.
     *
     * @param field The field to check.
     * @param value The threshold value.
     * @return The constructed Filter.
     */
    public static Filter gt(String field, Comparable<?> value) {
        return FluentFilter.where(field).gt(value);
    }

    /**
     * Creates a greater-than-or-equal filter.
     *
     * @param field The field to check.
     * @param value The threshold value.
     * @return The constructed Filter.
     */
    public static Filter gte(String field, Comparable<?> value) {
        return FluentFilter.where(field).gte(value);
    }

    /**
     * Creates a less-than filter.
     *
     * @param field The field to check.
     * @param value The threshold value.
     * @return The constructed Filter.
     */
    public static Filter lt(String field, Comparable<?> value) {
        return FluentFilter.where(field).lt(value);
    }

    /**
     * Creates a less-than-or-equal filter.
     *
     * @param field The field to check.
     * @param value The threshold value.
     * @return The constructed Filter.
     */
    public static Filter lte(String field, Comparable<?> value) {
        return FluentFilter.where(field).lte(value);
    }

    /**
     * Creates an IN filter.
     *
     * @param field  The field to check.
     * @param values The values to match.
     * @return The constructed Filter.
     */
    public static Filter in(String field, @Nullable Comparable<?>... values) {
        return FluentFilter.where(field).in(values);
    }

    /**
     * Creates a NOT IN filter.
     *
     * @param field  The field to check.
     * @param values The values to not match.
     * @return The constructed Filter.
     */
    public static Filter notIn(String field, @Nullable Comparable<?>... values) {
        return FluentFilter.where(field).notIn(values);
    }

    /**
     * Creates a regex filter.
     *
     * @param field The field to check.
     * @param value The regex value.
     * @return The constructed Filter.
     */
    public static Filter regex(String field, @Nullable String value) {
        return FluentFilter.where(field).regex(value);
    }

    /**
     * Creates a text match filter.
     *
     * @param field The field to check.
     * @param value The text value.
     * @return The constructed Filter.
     */
    public static Filter text(String field, @Nullable String value) {
        return FluentFilter.where(field).text(value);
    }

    /**
     * Creates an exists filter.
     *
     * @param field The field to check.
     * @return The constructed Filter.
     */
    public static Filter exists(String field) {
        return FluentFilter.where(field).exists();
    }

    /**
     * Creates a between filter, both bounds inclusive. Nitrite composes this from a
     * greater-or-equal and a lesser-or-equal filter, so both bounds take part in index selection.
     *
     * @param field The field to check.
     * @param lower The lower bound, inclusive.
     * @param upper The upper bound, inclusive.
     * @return The constructed Filter.
     */
    public static Filter between(String field, Comparable<?> lower, Comparable<?> upper) {
        return FluentFilter.where(field).between(lower, upper);
    }

    /**
     * Creates an element match filter.
     *
     * @param field  The field to check.
     * @param filter The sub-filter to match elements.
     * @return The constructed Filter.
     */
    public static Filter elemMatch(String field, @Nullable Filter filter) {
        return FluentFilter.where(field).elemMatch(filter);
    }

}

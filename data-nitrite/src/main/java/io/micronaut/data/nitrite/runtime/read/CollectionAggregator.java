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
package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.nitrite.runtime.NameUtils;
import org.dizitart.no2.collection.Document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy for aggregation operations (Max, Min, Sum, Avg) on Nitrite documents.
 * Used for methods like findMaxAgeByName() or findMinDateOfBirthByNameRegex().
 *
 * @since 5.2.0
 */
@Internal
public final class CollectionAggregator {

    private static final Pattern AGG_FUNC_PATTERN = Pattern.compile("^(?:find|get|read)(Max|Min|Sum|Avg)[A-Z][a-zA-Z0-9]*By");
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^(?:find|get|read)(Max|Min|Sum|Avg)([A-Z][a-zA-Z0-9]*)By");
    private static final Pattern IS_AGG_PATTERN = Pattern.compile("^(find|get|read)(Max|Min|Sum|Avg)[A-Z][a-zA-Z0-9]*By.*");

    /**
     * Create a new aggregator.
     */
    public CollectionAggregator() {
    }

    /**
     * Execute aggregation on a list of documents.
     *
     * @param docs the documents
     * @param fieldName the field name to aggregate
     * @param aggFunc the aggregation function (Max, Min, Sum, Avg)
     * @return the aggregated result, or null if no documents
     */
    public @Nullable Object aggregate(@Nullable List<Document> docs, String fieldName, String aggFunc) {
        if (docs == null || docs.isEmpty()) {
            return null;
        }

        // Try to get values - field might be stored as camelCase or snake_case
        List<Object> values = docs.stream()
            .map(d -> {
                Object val = d.get(fieldName);
                if (val == null) {
                    // Try snake_case conversion
                    String snakeCase = NameUtils.camelToSnake(fieldName);
                    if (!snakeCase.equals(fieldName)) {
                        val = d.get(snakeCase);
                    }
                }
                return val;
            })
            .filter(Objects::nonNull)
            .toList();

        if (values.isEmpty()) {
            return null;
        }

        return executeAggregate(aggFunc, values);
    }

    /**
     * Execute aggregation on a list of values.
     *
     * @param aggFunc the aggregation function (Max, Min, Sum, Avg)
     * @param values the values to aggregate
     * @return the aggregated result
     */
    private @Nullable Object executeAggregate(String aggFunc, List<Object> values) {
        if (values.isEmpty()) {
            return null;
        }

        Object first = values.getFirst();

        // Handle numeric aggregation
        if (first instanceof Number) {
            // Exact numeric inputs stay exact rather than being coerced through double.
            List<Number> numValues = values.stream().map(v -> (Number) v).toList();
            return switch (aggFunc) {
                case "Max" -> numValues.stream().max(CollectionAggregator::compareNumbers).orElse(null);
                case "Min" -> numValues.stream().min(CollectionAggregator::compareNumbers).orElse(null);
                case "Sum" -> aggregateNumbers(numValues, false);
                case "Avg" -> aggregateNumbers(numValues, true);
                default -> 0;
            };
        }

        // Handle LocalDate aggregation (stored as epoch day; values may be pre-converted)
        if (first instanceof LocalDate) {
            List<LocalDate> dates = values.stream().map(v -> (LocalDate) v).toList();
            return switch (aggFunc) {
                case "Max" -> dates.stream().max(LocalDate::compareTo).orElse(null);
                case "Min" -> dates.stream().min(LocalDate::compareTo).orElse(null);
                default -> null;
            };
        }

        // Handle LocalDateTime aggregation (stored as epoch nanos; values may be pre-converted)
        if (first instanceof LocalDateTime) {
            List<LocalDateTime> dateTimes = values.stream().map(v -> (LocalDateTime) v).toList();
            return switch (aggFunc) {
                case "Max" -> dateTimes.stream().max(LocalDateTime::compareTo).orElse(null);
                case "Min" -> dateTimes.stream().min(LocalDateTime::compareTo).orElse(null);
                default -> null;
            };
        }

        // Handle String values that might be dates
        if (first instanceof String) {
            try {
                List<LocalDate> dates = values.stream()
                    .map(v -> LocalDate.parse((String) v))
                    .toList();
                return switch (aggFunc) {
                    case "Max" -> dates.stream().max(LocalDate::compareTo).orElse(null);
                    case "Min" -> dates.stream().min(LocalDate::compareTo).orElse(null);
                    default -> null;
                };
            } catch (Exception e) {
                // Not a parseable date string, fall through to generic Comparable handling
            }
        }

        // Generic fallback for other Comparable types
        if (first instanceof Comparable) {
            if (aggFunc.equals("Max")) {
                return values.stream().max((a, b) -> ((Comparable<Object>) a).compareTo(b)).orElse(null);
            } else if (aggFunc.equals("Min")) {
                return values.stream().min((a, b) -> ((Comparable<Object>) a).compareTo(b)).orElse(null);
            }
        }

        return null;
    }

    private static int compareNumbers(Number left, Number right) {
        return toBigDecimal(left).compareTo(toBigDecimal(right));
    }

    /**
     * Sums or averages the given values. The result type follows the category of the inputs rather
     * than the numeric type they happened to be stored as: floating-point inputs produce a
     * {@link Double}, decimal inputs a {@link BigDecimal}, and integral inputs an exact integral
     * sum ({@link Long}, widened to {@link BigInteger} when it no longer fits). An average over
     * exact inputs is a {@link BigDecimal} because it is rarely integral.
     *
     * @param values  the values to aggregate, never empty
     * @param average true to average, false to sum
     * @return the aggregated value
     */
    private static Number aggregateNumbers(List<Number> values, boolean average) {
        boolean decimal = values.stream().anyMatch(value -> value instanceof BigDecimal);
        boolean floating = values.stream()
            .anyMatch(value -> value instanceof Double || value instanceof Float);
        if (floating && !decimal) {
            double sum = values.stream().mapToDouble(Number::doubleValue).sum();
            return average ? sum / values.size() : sum;
        }
        BigDecimal sum = values.stream()
            .map(CollectionAggregator::toBigDecimal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (average) {
            return sum.divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128);
        }
        if (decimal) {
            return sum;
        }
        BigInteger integral = sum.toBigIntegerExact();
        return integral.bitLength() < Long.SIZE ? integral.longValue() : integral;
    }

    private static BigDecimal toBigDecimal(Number value) {
        return switch (value) {
            case BigDecimal decimal -> decimal;
            case BigInteger integer -> new BigDecimal(integer);
            case Byte ignored -> BigDecimal.valueOf(value.longValue());
            case Short ignored -> BigDecimal.valueOf(value.longValue());
            case Integer ignored -> BigDecimal.valueOf(value.longValue());
            case Long ignored -> BigDecimal.valueOf(value.longValue());
            default -> BigDecimal.valueOf(value.doubleValue());
        };
    }

    /**
     * Extract aggregation function from method name.
     *
     * @param methodName the method name
     * @return the aggregation function (Max, Min, Sum, Avg), or null if not an aggregation method
     */
    public @Nullable String extractAggFunc(String methodName) {
        Matcher matcher = AGG_FUNC_PATTERN.matcher(methodName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extract field name from aggregation method name.
     *
     * @param methodName the method name
     * @return the field name, or null if not an aggregation method
     */
    public @Nullable String extractFieldName(String methodName) {
        Matcher matcher = FIELD_NAME_PATTERN.matcher(methodName);
        if (matcher.find()) {
            String fieldName = matcher.group(2);
            return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        return null;
    }

    /**
     * Check if a method name is an aggregation method.
     *
     * @param methodName the method name
     * @return true if it's an aggregation method
     */
    public boolean isAggregationMethod(String methodName) {
        if (methodName == null || methodName.length() < 10) {
            return false;
        }
        return IS_AGG_PATTERN.matcher(methodName).matches();
    }
}

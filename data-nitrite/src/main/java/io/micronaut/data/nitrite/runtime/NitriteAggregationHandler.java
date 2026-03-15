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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.Internal;
import org.dizitart.no2.collection.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Strategy for aggregation operations (Max, Min, Sum, Avg) on Nitrite documents.
 * Used for methods like findMaxAgeByName() or findMinDateOfBirthByNameRegex().
 *
 * @since 5.0.0
 */
@Internal
public final class NitriteAggregationHandler {

    /**
     * Execute aggregation on a list of documents.
     *
     * @param docs the documents
     * @param fieldName the field name to aggregate
     * @param aggFunc the aggregation function (Max, Min, Sum, Avg)
     * @return the aggregated result, or null if no documents
     */
    public Object aggregate(List<Document> docs, String fieldName, String aggFunc) {
        if (docs == null || docs.isEmpty()) {
            return null;
        }

        List<Object> values = docs.stream()
            .map(d -> d.get(fieldName))
            .filter(v -> v != null)
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
    private Object executeAggregate(String aggFunc, List<Object> values) {
        if (values.isEmpty()) {
            return null;
        }

        Object first = values.get(0);

        // Handle numeric aggregation
        if (first instanceof Number) {
            List<Number> numValues = values.stream().map(v -> (Number) v).toList();
            return switch (aggFunc) {
                case "Max" -> numValues.stream().mapToDouble(Number::doubleValue).max().orElse(0);
                case "Min" -> numValues.stream().mapToDouble(Number::doubleValue).min().orElse(0);
                case "Sum" -> numValues.stream().mapToDouble(Number::doubleValue).sum();
                case "Avg" -> numValues.stream().mapToDouble(Number::doubleValue).average().orElse(0);
                default -> 0;
            };
        }

        // Handle LocalDate aggregation (stored as String in Nitrite)
        if (first instanceof LocalDate) {
            List<LocalDate> dates = values.stream().map(v -> (LocalDate) v).toList();
            return switch (aggFunc) {
                case "Max" -> dates.stream().max(LocalDate::compareTo).orElse(null);
                case "Min" -> dates.stream().min(LocalDate::compareTo).orElse(null);
                default -> null;
            };
        }

        // Handle LocalDateTime aggregation (stored as String in Nitrite)
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
                // Not a date string, fall through to generic Comparable handling
            }
        }

        // Generic fallback for other Comparable types
        if (first instanceof Comparable) {
            if (aggFunc.equals("Max")) {
                return values.stream().max((a, b) -> ((Comparable) a).compareTo(b)).orElse(null);
            } else if (aggFunc.equals("Min")) {
                return values.stream().min((a, b) -> ((Comparable) a).compareTo(b)).orElse(null);
            }
        }

        return null;
    }

    /**
     * Extract aggregation function from method name.
     *
     * @param methodName the method name
     * @return the aggregation function (Max, Min, Sum, Avg), or null if not an aggregation method
     */
    public String extractAggFunc(String methodName) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(?:find|get|read)(Max|Min|Sum|Avg)[A-Z][a-zA-Z0-9]*By");
        java.util.regex.Matcher matcher = pattern.matcher(methodName);
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
    public String extractFieldName(String methodName) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(?:find|get|read)(Max|Min|Sum|Avg)([A-Z][a-zA-Z0-9]*)By");
        java.util.regex.Matcher matcher = pattern.matcher(methodName);
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
        return methodName.matches("^(find|get|read)(Max|Min|Sum|Avg)[A-Z][a-zA-Z0-9]*By.*");
    }
}

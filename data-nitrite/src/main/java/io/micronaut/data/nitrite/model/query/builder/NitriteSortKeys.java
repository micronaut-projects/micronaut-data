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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The two shapes a {@code $sort} takes, and the translation between them and {@link NitriteSortKey}.
 *
 * <p>A sort whose every key is a stored field with no null placement of its own keeps the map shape
 * {@code {"field": 1}} it has always had, so a query compiled before sort keys existed still reads
 * the same. Anything a map cannot say - an expression key, or a null placement - is written as a
 * list of key objects instead.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteSortKeys {

    private NitriteSortKeys() {
    }

    /**
     * Encodes sort keys for a query string.
     *
     * @param keys the sort keys
     * @return the map shape when it can carry every key, the list shape otherwise
     */
    public static Object toJson(List<NitriteSortKey> keys) {
        boolean mappable = keys.stream()
            .allMatch(key -> !key.isExpression() && key.nullOrdering() == Sort.Order.NullOrdering.NONE);
        if (mappable) {
            Map<String, Object> sortObj = new LinkedHashMap<>();
            for (NitriteSortKey key : keys) {
                sortObj.put(key.field(), key.ascending() ? 1 : -1);
            }
            return sortObj;
        }
        List<Object> encoded = new ArrayList<>(keys.size());
        for (NitriteSortKey key : keys) {
            Map<String, Object> entry = new LinkedHashMap<>();
            if (key.isExpression()) {
                entry.put(NitriteQueryOperators.EXPR, key.expression());
            } else {
                entry.put(NitriteQueryOperators.SORT_KEY, key.field());
            }
            entry.put(NitriteQueryOperators.SORT_DIR, key.ascending() ? 1 : -1);
            if (key.nullOrdering() != Sort.Order.NullOrdering.NONE) {
                entry.put(NitriteQueryOperators.SORT_NULLS, key.nullOrdering().name());
            }
            encoded.add(entry);
        }
        return encoded;
    }

    /**
     * Decodes the value of a {@code $sort}, in either shape.
     *
     * @param sortValue the parsed {@code $sort} value
     * @return the sort keys, empty when the value carries none
     */
    public static List<NitriteSortKey> fromJson(@Nullable Object sortValue) {
        if (sortValue instanceof Map<?, ?> sortMap) {
            List<NitriteSortKey> keys = new ArrayList<>(sortMap.size());
            for (Map.Entry<?, ?> entry : sortMap.entrySet()) {
                keys.add(NitriteSortKey.ofField(entry.getKey().toString(), isAscending(entry.getValue()),
                    Sort.Order.NullOrdering.NONE));
            }
            return keys;
        }
        if (sortValue instanceof List<?> sortList) {
            List<NitriteSortKey> keys = new ArrayList<>(sortList.size());
            for (Object element : sortList) {
                if (!(element instanceof Map<?, ?> entry)) {
                    continue;
                }
                boolean ascending = isAscending(entry.get(NitriteQueryOperators.SORT_DIR));
                Sort.Order.NullOrdering nullOrdering = nullOrdering(entry.get(NitriteQueryOperators.SORT_NULLS));
                Object expression = entry.get(NitriteQueryOperators.EXPR);
                if (expression != null) {
                    keys.add(NitriteSortKey.ofExpression(expression, ascending, nullOrdering));
                } else if (entry.get(NitriteQueryOperators.SORT_KEY) != null) {
                    keys.add(NitriteSortKey.ofField(entry.get(NitriteQueryOperators.SORT_KEY).toString(), ascending, nullOrdering));
                }
            }
            return keys;
        }
        return List.of();
    }

    /**
     * Reads sort keys off a runtime {@link Sort}.
     *
     * @param sort the sort, may be unsorted
     * @return the sort keys, empty when the sort carries none
     */
    public static List<NitriteSortKey> fromSort(@Nullable Sort sort) {
        if (sort == null || !sort.isSorted()) {
            return List.of();
        }
        List<NitriteSortKey> keys = new ArrayList<>(sort.getOrderBy().size());
        for (Sort.Order order : sort.getOrderBy()) {
            keys.add(NitriteSortKey.ofField(order.getProperty(),
                order.getDirection() == Sort.Order.Direction.ASC, order.getNullOrdering()));
        }
        return keys;
    }

    /**
     * Expresses sort keys as a {@link Sort}, for a caller that can only take one - cursored
     * pagination, which pages on stored fields. A computed key has no property name to page on and
     * is left out.
     *
     * @param keys the sort keys
     * @return the orders, empty when no key is a stored field
     */
    public static List<Sort.Order> toOrders(List<NitriteSortKey> keys) {
        List<Sort.Order> orders = new ArrayList<>(keys.size());
        for (NitriteSortKey key : keys) {
            if (key.isExpression()) {
                continue;
            }
            String field = Objects.requireNonNull(key.field(), "A stored sort key must have a field");
            orders.add(new Sort.Order(field,
                key.ascending() ? Sort.Order.Direction.ASC : Sort.Order.Direction.DESC,
                false,
                key.nullOrdering()));
        }
        return orders;
    }

    private static boolean isAscending(@Nullable Object direction) {
        return !(direction instanceof Number number) || number.intValue() >= 1;
    }

    private static Sort.Order.NullOrdering nullOrdering(@Nullable Object nulls) {
        if (nulls == null) {
            return Sort.Order.NullOrdering.NONE;
        }
        return switch (nulls.toString().toUpperCase(Locale.ROOT)) {
            case "FIRST" -> Sort.Order.NullOrdering.FIRST;
            case "LAST" -> Sort.Order.NullOrdering.LAST;
            default -> Sort.Order.NullOrdering.NONE;
        };
    }
}

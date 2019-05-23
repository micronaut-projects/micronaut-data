/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;

import java.util.List;

/**
 * An interface for objects that can be sorted.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface Sort {

    /**
     * Constant for unsorted.
     */
    Sort UNSORTED = new DefaultSort();

    /**
     * Orders by the specified property name (defaults to ascending).
     *
     * @param propertyName The property name to order by
     * @return A new sort with the order applied
     */
    @NonNull
    Sort order(@NonNull String propertyName);

    /**
     * Adds an order object.
     *
     * @param order The order object
     * @return A new sort with the order applied
     */
    @NonNull Sort order(@NonNull Sort.Order order);

    /**
     * Orders by the specified property name and direction.
     *
     * @param propertyName The property name to order by
     * @param direction Either "asc" for ascending or "desc" for descending
     *
     * @return A new sort with the order applied
     */
    @NonNull Sort order(@NonNull String propertyName, @NonNull Sort.Order.Direction direction);

    /**
     * @return The order definitions for this sort.
     */
    @NonNull List<Order> getOrderBy();

    /**
     * @return Default unsorted sort instance.
     */
    static Sort unsorted() {
        return UNSORTED;
    }

    /**
     * Create a sort from the given list of orders.
     *
     * @param orderList The order list
     * @return The sort
     */
    static @NonNull Sort of(@Nullable List<Order> orderList) {
        if (CollectionUtils.isEmpty(orderList)) {
            return UNSORTED;
        }
        return new DefaultSort(orderList);
    }

    /**
     * The ordering of results.
     */
    class Order {
        private final String property;
        private final Direction direction;
        private final boolean ignoreCase;

        /**
         * Constructs an order for the given property in ascending order.
         * @param property The property
         */
        public Order(@NonNull String property) {
            this(property, Direction.ASC, false);
        }

        /**
         * Constructs an order for the given property with the given direction.
         * @param property The property
         * @param direction The direction
         * @param ignoreCase Whether to ignore case
         */
        public Order(@NonNull String property, @NonNull Direction direction, boolean ignoreCase) {
            ArgumentUtils.requireNonNull("direction", direction);
            ArgumentUtils.requireNonNull("property", property);
            this.direction = direction;
            this.property = property;
            this.ignoreCase = ignoreCase;
        }

        /**
         * @return Whether to ignore case when sorting
         */
        public boolean isIgnoreCase() {
            return ignoreCase;
        }

        /**
         * @return The direction order by
         */
        public Direction getDirection() {
            return direction;
        }

        /**
         * @return The property name to order by
         */
        public String getProperty() {
            return property;
        }

        /**
         * Creates a new order for the given property in descending order.
         *
         * @param property The property
         * @return The order instance
         */
        public static Order desc(String property) {
            return new Order(property, Direction.DESC, false);
        }

        /**
         * Creates a new order for the given property in ascending order.
         *
         * @param property The property
         * @return The order instance
         */
        public static Order asc(String property) {
            return new Order(property, Direction.ASC, false);
        }

        /**
         * Creates a new order for the given property in descending order.
         *
         * @param property The property
         * @param ignoreCase Whether to ignore case
         * @return The order instance
         */
        public static Order desc(String property, boolean ignoreCase) {
            return new Order(property, Direction.DESC, ignoreCase);
        }

        /**
         * Creates a new order for the given property in ascending order.
         *
         * @param property The property
         * @param ignoreCase  Whether to ignore case
         * @return The order instance
         */
        public static Order asc(String property, boolean ignoreCase) {
            return new Order(property, Direction.ASC, ignoreCase);
        }

        /**
         * @return Is the order ascending
         */
        public boolean isAscending() {
            return getDirection() == Direction.ASC;
        }

        /**
         * Represents the direction of the ordering.
         */
        public enum Direction {
            ASC, DESC
        }
    }
}

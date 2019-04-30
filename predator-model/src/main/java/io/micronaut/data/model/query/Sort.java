package io.micronaut.data.model.query;

import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * An interface for objects that can be sorted.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface Sort {

    /**
     * Orders by the specified property name (defaults to ascending).
     *
     * @param propertyName The property name to order by
     * @return This criteria
     */
    @Nonnull
    Sort order(@Nonnull String propertyName);

    /**
     * Adds an order object
     *
     * @param order The order object
     * @return The order object
     */
    @Nonnull Sort order(@Nonnull Sort.Order order);

    /**
     * Orders by the specified property name and direction.
     *
     * @param propertyName The property name to order by
     * @param direction Either "asc" for ascending or "desc" for descending
     *
     * @return This criteria
     */
    @Nonnull Sort order(@Nonnull String propertyName, @Nonnull Sort.Order.Direction direction);

    /**
     * @return The order definitions for this sort.
     */
    @Nonnull List<Order> getOrderBy();

    /**
     * @return Default unsorted sort instance.
     */
    static Sort unsorted() {
        return new DefaultSort();
    }

    /**
     * The ordering of results.
     */
    class Order {
        private final String property;
        private final Direction direction;
        private boolean ignoreCase = false;

        /**
         * Constructs an order for the given property in ascending order.
         * @param property The property
         */
        public Order(@Nonnull String property) {
            this(property, Direction.ASC);
        }

        /**
         * Constructs an order for the given property with the given direction.
         * @param property The property
         * @param direction The direction
         */
        public Order(@Nonnull String property, @Nonnull Direction direction) {
            ArgumentUtils.requireNonNull("direction", direction);
            ArgumentUtils.requireNonNull("property", property);
            this.direction = direction;
            this.property = property;
        }

        /**
         * Whether to ignore the case for this order definition
         *
         * @return This order instance
         */
        public Order ignoreCase() {
            this.ignoreCase = true;
            return this;
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
         * Creates a new order for the given property in descending order
         *
         * @param property The property
         * @return The order instance
         */
        public static Order desc(String property) {
            return new Order(property, Direction.DESC);
        }

        /**
         * Creates a new order for the given property in ascending order
         *
         * @param property The property
         * @return The order instance
         */
        public static Order asc(String property) {
            return new Order(property, Direction.ASC);
        }

        /**
         * Represents the direction of the ordering
         */
        public enum Direction {
            ASC, DESC
        }
    }
}

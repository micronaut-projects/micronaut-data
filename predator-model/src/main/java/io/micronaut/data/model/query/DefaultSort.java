package io.micronaut.data.model.query;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the sort interface.
 *
 * @author graemerocher
 * @since 1.0
 */
class DefaultSort implements Sort {
    private final List<Order> orderBy;

    DefaultSort(List<Order> orderBy) {
        this.orderBy = orderBy;
    }

    DefaultSort() {
        this.orderBy = new ArrayList<>(2);
    }

    /**
     * Specifies the order of results
     * @param order The order object
     * @return The Query instance
     */
    public @Nonnull DefaultSort order(@Nonnull Order order) {
        if (order != null) {
            orderBy.add(order);
        }
        return this;
    }

    /**
     * Gets the Order entries for this query
     * @return The order entries
     */
    @Override
    public @Nonnull List<Order> getOrderBy() {
        return orderBy;
    }

    @Nonnull
    @Override
    public DefaultSort order(@Nonnull String propertyName) {
        return order(new Order(propertyName));
    }

    @Nonnull
    @Override
    public DefaultSort order(@Nonnull String propertyName, @Nonnull Order.Direction direction) {
        return order(new Order(propertyName, direction));
    }
}

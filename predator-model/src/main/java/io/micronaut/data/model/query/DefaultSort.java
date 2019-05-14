package io.micronaut.data.model.query;

import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of the sort interface.
 *
 * @author graemerocher
 * @since 1.0
 */
class DefaultSort implements Sort {
    private final List<Order> orderBy;

    /**
     * Constructor that takes an order.
     * @param orderBy The order by
     */
    DefaultSort(List<Order> orderBy) {
        this.orderBy = orderBy;
    }

    /**
     * Default constructor.
     */
    DefaultSort() {
        this.orderBy = Collections.emptyList();
    }

    /**
     * Specifies the order of results
     * @param order The order object
     * @return The Query instance
     */
    public @NonNull DefaultSort order(@NonNull Order order) {
        ArgumentUtils.requireNonNull("order", order);
        List<Order> newOrderBy = new ArrayList<>(orderBy);
        newOrderBy.add(order);
        return new DefaultSort(newOrderBy);
    }

    /**
     * Gets the Order entries for this query
     * @return The order entries
     */
    @Override
    public @NonNull List<Order> getOrderBy() {
        return Collections.unmodifiableList(orderBy);
    }

    @NonNull
    @Override
    public DefaultSort order(@NonNull String propertyName) {
        return order(new Order(propertyName));
    }

    @NonNull
    @Override
    public DefaultSort order(@NonNull String propertyName, @NonNull Order.Direction direction) {
        return order(new Order(propertyName, direction, false));
    }
}

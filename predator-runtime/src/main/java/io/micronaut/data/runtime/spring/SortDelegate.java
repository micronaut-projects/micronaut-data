package io.micronaut.data.runtime.spring;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.Sort;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Supports representing a Spring Sort as a Micronaut {@link io.micronaut.data.model.Page}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class SortDelegate implements Sort {

    private org.springframework.data.domain.Sort sort;

    SortDelegate(org.springframework.data.domain.Sort sort) {
        this.sort = sort;
    }

    @Nonnull
    @Override
    public Sort order(@Nonnull String propertyName) {
        this.sort = this.sort.and(new org.springframework.data.domain.Sort(org.springframework.data.domain.Sort.Direction.ASC, propertyName));
        return this;
    }

    @Nonnull
    @Override
    public Sort order(@Nonnull Order order) {
        org.springframework.data.domain.Sort.Direction direction =
                order.isAscending() ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        String property = order.getProperty();
        org.springframework.data.domain.Sort springSort = toSpringSort(direction, property);
        this.sort = this.sort.and(
                springSort
        );
        return this;
    }

    private org.springframework.data.domain.Sort toSpringSort(org.springframework.data.domain.Sort.Direction direction, String property) {
        return new org.springframework.data.domain.Sort(
                direction,
                property
        );
    }

    @Nonnull
    @Override
    public Sort order(@Nonnull String propertyName, @Nonnull Order.Direction direction) {
        org.springframework.data.domain.Sort.Direction d = org.springframework.data.domain.Sort.Direction.valueOf(
                direction.name()
        );
        org.springframework.data.domain.Sort springSort = toSpringSort(d, propertyName);
        this.sort = this.sort.and(
                springSort
        );
        return this;
    }

    @Nonnull
    @Override
    public List<Order> getOrderBy() {
        return iteratorToList(sort.iterator());
    }

    /**
     * Convert an {@link Iterator} to a {@link Set}.
     *
     * @param iterator The iterator
     * @return The set
     */
    private static List<Order> iteratorToList(Iterator<org.springframework.data.domain.Sort.Order> iterator) {
        List<Order> list = new ArrayList<>();
        while (iterator.hasNext()) {
            org.springframework.data.domain.Sort.Order order = iterator.next();
            if (order.isAscending()) {
                list.add(Order.asc(order.getProperty()));
            } else {
                list.add(Order.desc(order.getProperty()));
            }
        }
        return list;
    }
}

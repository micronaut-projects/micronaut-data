/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.runtime.spring;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Sort;

import edu.umd.cs.findbugs.annotations.NonNull;
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

    /**
     * Default constructor.
     * @param sort The target sort
     */
    SortDelegate(org.springframework.data.domain.Sort sort) {
        this.sort = sort;
    }

    @Override
    public boolean isSorted() {
        return sort.isSorted();
    }

    @NonNull
    @Override
    public Sort order(@NonNull String propertyName) {
        this.sort = this.sort.and(new org.springframework.data.domain.Sort(org.springframework.data.domain.Sort.Direction.ASC, propertyName));
        return this;
    }

    @NonNull
    @Override
    public Sort order(@NonNull Order order) {
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

    @NonNull
    @Override
    public Sort order(@NonNull String propertyName, @NonNull Order.Direction direction) {
        org.springframework.data.domain.Sort.Direction d = org.springframework.data.domain.Sort.Direction.valueOf(
                direction.name()
        );
        org.springframework.data.domain.Sort springSort = toSpringSort(d, propertyName);
        this.sort = this.sort.and(
                springSort
        );
        return this;
    }

    @NonNull
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

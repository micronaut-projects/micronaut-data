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
import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Models pageable data. The {@link #from(int, int)} method can be used to construct a new instance to pass to Predator methods.
 *
 * @author boros
 * @author graemerocher
 * @since 1.0.0
 */
@Introspected
public interface Pageable extends Sort {

    /**
     * Constant for no pagination.
     */
    Pageable UNPAGED = new Pageable() {
        @Override
        public int getNumber() {
            return 0;
        }

        @Override
        public int getSize() {
            return 0;
        }
    };

    /**
     * @return The page number.
     */
    int getNumber();

    /**
     * Maximum size of the page to be returned. A value of -1 indicates no maximum.
     * @return size of the requested page of items
     */
    int getSize();

    /**
     * Offset in the requested collection. Defaults to zero.
     * @return offset in the requested collection
     */
    default long getOffset() {
        return getNumber() * getSize();
    }

    /**
     * @return The sort definition to use.
     */
    @NonNull
    default Sort getSort() {
        return Sort.unsorted();
    }

    /**
     * @return The next pageable.
     */
    default @NonNull Pageable next() {
        int size = getSize();
        int newNumber = getNumber() + 1;
        // handle overflow
        if (newNumber < 0) {
            return Pageable.from(0, size, getSort());
        } else {
            return Pageable.from(newNumber, size, getSort());
        }
    }

    /**
     * @return The previous pageable
     */
    default @NonNull Pageable previous() {
        int size = getSize();
        int newNumber = getNumber() - size;
        // handle overflow
        if (newNumber < 0) {
            return Pageable.from(0, size, getSort());
        } else {
            return Pageable.from(newNumber, size, getSort());
        }
    }

    @NonNull
    @Override
    default Pageable order(@NonNull String propertyName) {
        Sort newSort = getSort().order(propertyName);
        return Pageable.from(getNumber(), getSize(), newSort);
    }

    @Override
    default boolean isSorted() {
        return getSort().isSorted();
    }

    @NonNull
    @Override
    default Pageable order(@NonNull Order order) {
        Sort newSort = getSort().order(order);
        return Pageable.from(getNumber(), getSize(), newSort);
    }

    @NonNull
    @Override
    default Pageable order(@NonNull String propertyName, @NonNull Order.Direction direction) {
        Sort newSort = getSort().order(propertyName, direction);
        return Pageable.from(getNumber(), getSize(), newSort);
    }

    @NonNull
    @Override
    default List<Order> getOrderBy() {
        return getSort().getOrderBy();
    }

    /**
     * Creates a new {@link Pageable} at the given offset with a default size of 10.
     * @param index The index
     * @return The pageable
     */
    static @NonNull Pageable from(int index) {
        return new DefaultPageable(index, 10, null);
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param index The index
     * @param size the size
     * @return The pageable
     */
    static @NonNull Pageable from(int index, int size) {
        return new DefaultPageable(index, size, null);
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param number The offset
     * @param size the size
     * @param sort the sort
     * @return The pageable
     */
    static @NonNull Pageable from(int number, int size, @Nullable Sort sort) {
        return new DefaultPageable(number, size, sort);
    }

    /**
     * @return A new instance without paging data.
     */
    static @NonNull Pageable unpaged() {
        return UNPAGED;
    }
}

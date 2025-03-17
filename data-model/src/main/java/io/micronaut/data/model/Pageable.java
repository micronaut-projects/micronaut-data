/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Models pageable data. The {@link #from(int, int)} method can be used to construct a new instance to pass to Micronaut Data methods.
 *
 * @author boros
 * @author graemerocher
 * @since 1.0.0
 */
@Serdeable
@Introspected
@JsonIgnoreProperties(ignoreUnknown = true)
public interface Pageable extends Sort {

    /**
     * Constant for no pagination.
     */
    Pageable UNPAGED = new DefaultPageable(0, -1, Sort.UNSORTED, true);

    /**
     * @return The page page.
     */
    int getNumber();

    /**
     * Maximum size of the page to be returned. A value of -1 indicates no maximum.
     * @return size of the requested page of items
     */
    int getSize();

    /**
     * The pagination mode that is either offset pagination, currentCursor forward or currentCursor backward
     * pagination.
     *
     * @since 4.8.0
     * @return The pagination mode
     */
    default Mode getMode() {
        return Mode.OFFSET;
    }

    /**
     * Get the currentCursor in case cursored pagination is used.
     *
     * @since 4.8.0
     * @return The currentCursor
     */
    default Optional<Cursor> cursor() {
        return Optional.empty();
    }

    /**
     * Whether the returned page should contain information about total items that
     * can be produced by this query. If the value is false, {@link Page#getTotalSize()} and
     * {@link Page#getTotalPages()} methods will fail. By default, pageable will have this value
     * set to true.
     *
     * @since 4.8.0
     * @return Whether total size information is required.
     */
    default boolean requestTotal() {
        return true;
    }

    /**
     * Offset in the requested collection. Defaults to zero.
     * @return offset in the requested collection
     */
    @JsonIgnore
    default long getOffset() {
        int size = getSize();
        if (size < 0) {
            return 0; // unpaged
        }
        return (long) getNumber() * (long) size;
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
    @NonNull
    default Pageable next() {
        return getPageable(getNumber() + 1);
    }

    /**
     * @return The previous pageable
     */
    @NonNull
    default Pageable previous() {
        return getPageable(getNumber() - 1);
    }

    private Pageable getPageable(int newNumber) {
        int size = getSize();
        if (size < 0) {
            // unpaged
            return Pageable.from(0, size, getSort());
        }
        Pageable newPageable;
        // handle overflow
        if (newNumber < 0) {
            newPageable = Pageable.from(0, size, getSort());
        } else {
            newPageable = Pageable.from(newNumber, size, getSort());
        }
        if (!requestTotal()) {
            newPageable = newPageable.withoutTotal();
        }
        return newPageable;
    }

    /**
     * @return Whether it is unpaged
     */
    @JsonIgnore
    default boolean isUnpaged() {
        return getSize() == -1;
    }

    @NonNull
    @Override
    default Pageable order(@NonNull String propertyName) {
        Sort newSort = getSort().order(propertyName);
        return Pageable.from(getNumber(), getSize(), newSort);
    }

    @Override
    @JsonIgnore
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
    default Pageable orders(@NonNull List<Order> orders) {
        Sort newSort = getSort();
        for (Order order : orders) {
            newSort = newSort.order(order);
        }
        return Pageable.from(getNumber(), getSize(), newSort);
    }

    /**
     * Removes ordering.
     *
     * @since 4.10
     * @return A pageable without ordering
     */
    @NonNull
    default Pageable withoutSort() {
        if (isSorted()) {
            return Pageable.from(getNumber(), getSize());
        }
        return this;
    }

    /**
     * Removes paging.
     *
     * @since 4.10
     * @return A pageable without paging
     */
    @NonNull
    default Pageable withoutPaging() {
        return Pageable.UNPAGED.orders(getSort().getOrderBy());
    }

    /**
     * Creates a new {@link Pageable} with a custom sort.
     *
     * @param sort a new sort
     * @since 4.10
     * @return A pageable instance with a new sort
     */
    default Pageable withSort(@NonNull Sort sort) {
        return Pageable.from(getNumber(), getSize(), sort);
    }

    @NonNull
    @Override
    @JsonIgnore
    default List<Order> getOrderBy() {
        return getSort().getOrderBy();
    }

    /**
     * Specify that the {@link Page} response should have information about total size.
     *
     * @see #requestTotal() requestTotal() for more details.
     * @since 4.8.0
     * @return A pageable instance that will request the total size.
     */
    default Pageable withTotal() {
        if (this.requestTotal()) {
            return this;
        }
        throw new UnsupportedOperationException("Changing requestTotal is not supported");
    }

    /**
     * Specify that the {@link Page} response should not have information about total size.
     *
     * @see #requestTotal() requestTotal() for more details.
     * @since 4.8.0
     * @return A pageable instance that won't request the total size.
     */
    default Pageable withoutTotal() {
        if (!this.requestTotal()) {
            return this;
        }
        throw new UnsupportedOperationException("Changing requestTotal is not supported");
    }

    /**
     * Creates a new {@link Pageable} at the given offset with a default size of 10.
     * @param page The page
     * @return The pageable
     */
    static @NonNull Pageable from(int page) {
        return new DefaultPageable(page, 10, null, true);
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param page The page
     * @param size the size
     * @return The pageable
     */
    static @NonNull Pageable from(int page, int size) {
        return new DefaultPageable(page, size, null, true);
    }

    /**
     * Creates a new {@link Pageable} with the given offset.
     *
     * @param page The page
     * @param size the size
     * @param sort the sort
     * @return The pageable
     */
    static @NonNull Pageable from(
        int page,
        int size,
        @Nullable Sort sort
    ) {
        return new DefaultPageable(page, size, sort, true);
    }

    /**
     * Creates a new {@link Pageable} with the given parameters.
     * The method is used for deserialization and most likely should not be used as an API.
     *
     * @param page The page
     * @param size The size
     * @param mode The pagination mode
     * @param cursor The current cursor
     * @param sort The sort
     * @param requestTotal Whether to query total count
     * @return The pageable
     */
    @Internal
    @JsonCreator
    static @NonNull Pageable from(
            @JsonProperty("number") int page,
            @JsonProperty("size") int size,
            @JsonProperty("mode") @Nullable Mode mode,
            @JsonProperty("cursor") @Nullable Cursor cursor,
            @JsonProperty("sort") @Nullable Sort sort,
            @JsonProperty(value = "requestTotal", defaultValue = "true") boolean requestTotal
    ) {
        if (mode == null || mode == Mode.OFFSET) {
            return new DefaultPageable(page, size, sort, requestTotal);
        } else {
            return new DefaultCursoredPageable(
                size, cursor, mode, page, sort == null ? UNSORTED : sort, requestTotal
            );
        }
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param sort the sort
     * @return The pageable
     */
    static @NonNull Pageable from(Sort sort) {
        if (sort == null) {
            return UNPAGED;
        } else {
            return new DefaultPageable(0, -1, sort, true);
        }
    }

    /**
     * @return A new instance without paging data.
     */
    static @NonNull Pageable unpaged() {
        return UNPAGED;
    }

    /**
     * Create a new {@link Pageable} for forward pagination given the currentCursor after which to query.
     *
     * @since 4.8.0
     * @param cursor The currentCursor
     * @param page The page page
     * @param size The page size
     * @param sort The sorting
     * @return The pageable
     */
    static @NonNull CursoredPageable afterCursor(@NonNull Cursor cursor, int page, int size, @Nullable Sort sort) {
        if (sort == null) {
            sort = UNSORTED;
        }
        return new DefaultCursoredPageable(size, cursor, Mode.CURSOR_NEXT, page, sort, true);
    }

    /**
     * Create a new {@link Pageable} for backward pagination given the currentCursor after which to query.
     *
     * @since 4.8.0
     * @param cursor The currentCursor
     * @param page The page page
     * @param size The page size
     * @param sort The sorting
     * @return The pageable
     */
    static @NonNull CursoredPageable beforeCursor(@NonNull Cursor cursor, int page, int size, @Nullable Sort sort) {
        if (sort == null) {
            sort = UNSORTED;
        }
        return new DefaultCursoredPageable(size, cursor, Mode.CURSOR_PREVIOUS, page, sort, true);
    }

    /**
     * The type of pagination: offset-based or currentCursor-based, which includes
     * a direction.
     *
     * @since 4.8.0
     */
    enum Mode {
        /**
         * Indicates forward currentCursor-based pagination, which follows the
         * direction of the sort criteria, using a currentCursor that is
         * formed from the key of the last entity on the current page.
         */
        CURSOR_NEXT,

        /**
         * Indicates a request for a page with currentCursor-based pagination
         * in the previous page direction to the sort criteria, using a currentCursor
         * that is formed from the key of first entity on the current page.
         * The order of results on each page follows the sort criteria
         * and is not reversed.
         */
        CURSOR_PREVIOUS,

        /**
         * Indicates a request for a page using offset pagination.
         * The starting position for pages is computed as an offset from
         * the first result based on the page page and maximum page size.
         * Offset pagination is used when a currentCursor is not supplied.
         */
        OFFSET
    }

    /**
     * An interface for defining pagination cursors.
     * It is generally a list of elements which can be used to create a query for the next
     * or previous page.
     *
     * @since 4.8.0
     */
    @Serdeable
    interface Cursor {
        /**
         * Returns the currentCursor element at the specified position.
         * @param index The index of the currentCursor value
         * @return The currentCursor value
         */
        Object get(int index);

        /**
         * Returns all the currentCursor values in a list.
         * @return The currentCursor values
         */
        List<Object> elements();

        /**
         * @return The page of elements in the currentCursor.
         */
        int size();

        /**
         * Create a currentCursor from elements.
         * @param elements The currentCursor elements
         * @return The currentCursor
         */
        static Cursor of(Object... elements) {
            return new DefaultCursoredPageable.DefaultCursor(Arrays.asList(elements));
        }

        /**
         * Create a currentCursor from elements.
         * @param elements The currentCursor elements
         * @return The currentCursor
         */
        @JsonCreator
        static Cursor of(List<Object> elements) {
            return new DefaultCursoredPageable.DefaultCursor(elements);
        }
    }
}

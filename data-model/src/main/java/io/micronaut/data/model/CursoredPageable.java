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
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Optional;

/**
 * Models pageable data that uses a cursor.
 *
 * @author Andriy Dmytruk
 * @since 4.8.0
 */
@Serdeable
@Introspected
@JsonIgnoreProperties(ignoreUnknown = true)
public interface CursoredPageable extends Pageable {

    /**
     * Constant for no pagination.
     */
    CursoredPageable UNPAGED = new DefaultCursoredPageable(
        -1, null, null, false, 0, Sort.UNSORTED, true
    );

    /**
     * @return The cursor values corresponding to the beginning of queried data.
     * This cursor is used for forward pagination.
     */
    @Nullable
    Cursor getStartCursor();

    /**
     * @return The cursor values corresponding to the end of queried data.
     * This cursor is used for backward pagination.
     */
    @Nullable
    Cursor getEndCursor();

    /**
     * Whether the pageable is traversing backwards.
     *
     * @return Whether cursor is going in reverse direction.
     */
    boolean isBackward();

    @Override
    default Mode getMode() {
        return isBackward() ? Mode.CURSOR_PREVIOUS : Mode.CURSOR_NEXT;
    }

    @Override
    default Optional<Cursor> cursor() {
        return isBackward() ? Optional.ofNullable(getEndCursor()) : Optional.ofNullable(getStartCursor());
    }

    @Override
    default @NonNull CursoredPageable next() {
        throw new IllegalStateException("To get the next CursoredPageable, you must retrieve this one from a page");
    }

    @Override
    default @NonNull CursoredPageable previous() {
        throw new IllegalStateException("To get the next CursoredPageable, you must retrieve this one from a page");
    }

    /**
     * @return Whether there is a next page
     */
    default boolean hasNext() {
        return false;
    }

    /**
     * @return Whether there is a previous page.
     */
    default boolean hasPrevious() {
        return false;
    }

    /**
     * Creates a new {@link CursoredPageable} with the given sort.
     *
     * @param sort The sort
     * @return The pageable
     */
    static @NonNull CursoredPageable from(Sort sort) {
        if (sort == null) {
            return UNPAGED;
        }
        return new DefaultCursoredPageable(
            -1, null, null, false, 0, sort, true
        );
    }

    /**
     * Creates a new {@link CursoredPageable} with the given sort and page size.
     *
     * @param size The page size
     * @param sort The sort
     * @return The pageable
     */
    static @NonNull CursoredPageable from(
        @JsonProperty("size") int size,
        @JsonProperty("sort") @Nullable Sort sort
    ) {
        if (sort == null) {
            sort = UNSORTED;
        }
        return new DefaultCursoredPageable(size, null, null, false, 0, sort, true);
    }

    /**
     * Creates a new {@link CursoredPageable} with the given cursor.
     *
     * @param page The page
     * @param startCursor The cursor pointing to the beginning of the traversed data.
     * @param endCursor The cursor pointing to the end the traversed data.
     * @param isBackward Whether the cursor is for backward traversing
     * @param size The page size
     * @param sort The sort
     * @return The pageable
     */
    @JsonCreator
    static @NonNull CursoredPageable from(
        @JsonProperty("number") int page,
        @JsonProperty("startCursor") @Nullable Cursor startCursor,
        @JsonProperty("endCursor") @Nullable Cursor endCursor,
        @JsonProperty(value = "isBackward", defaultValue = "false") boolean isBackward,
        @JsonProperty("size") int size,
        @JsonProperty("sort") @Nullable Sort sort
    ) {
        if (sort == null) {
            sort = UNSORTED;
        }
        return new DefaultCursoredPageable(size, startCursor, endCursor, isBackward, page, sort, true);
    }

    /**
     * @return A new instance without paging data.
     */
    static @NonNull CursoredPageable unpaged() {
        return UNPAGED;
    }

    /**
     * Default implementation of the {@link Cursor}.
     *
     * @param elements The cursor elements
     */
    record DefaultCursor(
        List<Object> elements
    ) implements Cursor {
        @Override
        public Object get(int index) {
            return elements.get(index);
        }

        @Override
        public int size() {
            return elements.size();
        }
    }
}
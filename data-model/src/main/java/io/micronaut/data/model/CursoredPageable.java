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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Models a pageable request that uses a cursor.
 *
 * @author Andriy Dmytruk
 * @since 4.8.0
 */
@Serdeable
@Introspected
@JsonIgnoreProperties(ignoreUnknown = true)
public interface CursoredPageable extends Pageable {

    /**
     * Whether the pageable is traversing backwards.
     *
     * @return Whether currentCursor is going in reverse direction.
     */
    boolean isBackward();

    @Override
    default Mode getMode() {
        return isBackward() ? Mode.CURSOR_PREVIOUS : Mode.CURSOR_NEXT;
    }

    /**
     * Creates a new {@link CursoredPageable} with the given sort.
     *
     * @param sort The sort
     * @return The pageable
     */
    static @NonNull CursoredPageable from(Sort sort) {
        if (sort == null) {
            sort = Sort.UNSORTED;
        }
        return new DefaultCursoredPageable(
            -1, null, Mode.CURSOR_NEXT, 0, sort, true
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
        return new DefaultCursoredPageable(size, null, Mode.CURSOR_NEXT, 0, sort, true);
    }

    /**
     * Creates a new {@link CursoredPageable} with the given currentCursor.
     *
     * @param page The page
     * @param cursor The current currentCursor that will be used for querying data.
     * @param mode The pagination mode. Must be either forward or backward currentCursor pagination.
     * @param size The page size
     * @param sort The sort
     * @param requestTotal Whether to request total count
     * @return The pageable
     */
    @Internal
    @JsonCreator
    static @NonNull CursoredPageable from(
        @JsonProperty("number") int page,
        @Nullable Cursor cursor,
        Pageable.Mode mode,
        int size,
        @Nullable Sort sort,
        boolean requestTotal
    ) {
        if (sort == null) {
            sort = UNSORTED;
        }
        return new DefaultCursoredPageable(size, cursor, mode, page, sort, requestTotal);
    }

    @Override
    CursoredPageable withoutSort();

    @Override
    CursoredPageable withoutPaging();

    @Override
    CursoredPageable withSort(Sort sort);

    @Override
    CursoredPageable withTotal();

    @Override
    CursoredPageable withoutTotal();

    @Override
    CursoredPageable orders(List<Order> orders);
}

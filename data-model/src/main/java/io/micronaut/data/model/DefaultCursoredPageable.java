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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The default cursored pageable implementation.
 *
 * @param page The page.
 * @param currentCursor The current currentCursor. This is the currentCursor that will be used for pagination
 *               in case this pageable is used in a query.
 * @param nextCursor A currentCursor for the next page of data. It is stored in pageable to correctly
 *                   support {@link #next()} for forward or {@link #previous()} for backward
 *                   pagination.
 * @param mode The pagination mode. Could be one of {@link Mode#CURSOR_NEXT} or {@link Mode#CURSOR_PREVIOUS}.
 * @param size The size of a page
 * @param sort The sorting
 * @param requestTotal Whether to request the total count
 *
 * @author Andriy Dmytruk
 * @since 4.8.0
 */
@Serdeable
record DefaultCursoredPageable(
    int size,
    @Nullable
    @JsonProperty("cursor")
    Cursor currentCursor,
    @Nullable
    Cursor nextCursor,
    Mode mode,
    @JsonProperty("number") int page,
    Sort sort,
    boolean requestTotal
) implements CursoredPageable {

    /**
     * Default constructor.
     */
    @Creator
    DefaultCursoredPageable {
        if (page < 0) {
            throw new IllegalArgumentException("Page index cannot be negative");
        }
        if (size == 0) {
            throw new IllegalArgumentException("Size cannot be 0");
        }
        if (mode != Mode.CURSOR_NEXT && mode != Mode.CURSOR_PREVIOUS) {
            throw new IllegalArgumentException("The pagination mode must be either currentCursor forward or currentCursor backward");
        }
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public Optional<Cursor> cursor() {
        return Optional.ofNullable(currentCursor);
    }

    @Override
    public int getNumber() {
        return page;
    }

    @NonNull
    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public boolean isBackward() {
        return mode == Mode.CURSOR_PREVIOUS;
    }

    @Override
    public CursoredPageable next() {
        Cursor requiredCursor = mode == Mode.CURSOR_PREVIOUS ? currentCursor : nextCursor;
        if (requiredCursor != null) {
            return new DefaultCursoredPageable(
                    size,
                    requiredCursor,
                    null,
                    Mode.CURSOR_NEXT,
                    page + 1,
                    sort,
                    requestTotal
            );
        }
        return CursoredPageable.super.next();
    }

    @Override
    public CursoredPageable previous() {
        Cursor requiredCursor = mode == Mode.CURSOR_PREVIOUS ? nextCursor : currentCursor;
        if (requiredCursor != null) {
            return new DefaultCursoredPageable(
                    size,
                    requiredCursor,
                    null,
                    Mode.CURSOR_PREVIOUS,
                    Math.max(page - 1, 0),
                    sort,
                    requestTotal
            );
        }
        return CursoredPageable.super.previous();
    }

    @Override
    public Pageable withTotal() {
        if (requestTotal) {
            return this;
        }
        return new DefaultCursoredPageable(size, currentCursor, nextCursor, mode, page, sort, true);
    }

    @Override
    public Pageable withoutTotal() {
        if (!requestTotal) {
            return this;
        }
        return new DefaultCursoredPageable(size, currentCursor, nextCursor, mode, page, sort, true);
    }

    @Override
    public boolean hasNext() {
        return mode == Mode.CURSOR_PREVIOUS ? currentCursor != null : nextCursor != null;
    }

    @Override
    public boolean hasPrevious() {
        return mode == Mode.CURSOR_PREVIOUS ? nextCursor != null : currentCursor != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCursoredPageable that)) {
            return false;
        }
        return size == that.size
            && Objects.equals(currentCursor, that.currentCursor)
            && Objects.equals(nextCursor, that.nextCursor)
            && Objects.equals(mode, that.mode)
            && Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, currentCursor, nextCursor, mode, sort);
    }

    @Override
    public String toString() {
        return "DefaultCursoredPageable{" +
                "size=" + size +
                ", page=" + page +
                ", currentCursor=" + currentCursor +
                ", nextCursor=" + nextCursor +
                ", mode=" + mode +
                ", sort=" + sort +
                '}';
    }

    /**
     * Default implementation of the {@link Cursor}.
     *
     * @param elements The currentCursor elements
     */
    @Serdeable
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

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

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The default cursored pageable implementation.
 *
 * @author Andriy Dmytruk
 * @since 4.6.1
 */
@Introspected
final class DefaultCursoredPageable implements CursoredPageable {

    private final int max;
    private final int number;
    private final List<Object> startCursor;
    private final List<Object> endCursor;
    private final boolean isBackward;
    private final Sort sort;

    /**
     * Default constructor.
     *
     * @param page The page.
     * @param startCursor The cursor that is pointing to the start of the data.
     *                    This cursor will be used for forward pagination.
     * @param endCursor The cursor that is pointing to the end of the data.
     *                  This cursor will be used for backward pagination
     * @param isBackward Whether user requested for backward pagination.
     * @param size The size of a page
     * @param sort The sorting
     */
    @Creator
    DefaultCursoredPageable(int page, @Nullable List<Object> startCursor, @Nullable List<Object> endCursor, boolean isBackward, int size, @Nullable Sort sort) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index cannot be negative");
        }
        if (size == 0) {
            throw new IllegalArgumentException("Size cannot be 0");
        }
        this.max = size;
        this.number = page;
        this.startCursor = startCursor;
        this.endCursor = endCursor;
        this.isBackward = isBackward;
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    @Override
    public int getSize() {
        return max;
    }

    @Override
    public int getNumber() {
        return number;
    }

    @NonNull
    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public List<Object> getStartCursor() {
        return startCursor;
    }

    @Override
    public List<Object> getEndCursor() {
        return endCursor;
    }

    @Override
    public boolean isBackward() {
        return isBackward;
    }

    @Override
    public CursoredPageable next() {
        if (endCursor != null) {
            return new DefaultCursoredPageable(
                    number + 1,
                    endCursor,
                    null,
                    false,
                    getSize(),
                    getSort()
            );
        }
        return CursoredPageable.super.next();
    }

    @Override
    public CursoredPageable previous() {
        if (startCursor != null) {
            return new DefaultCursoredPageable(
                    Math.max(number - 1, 0),
                    null,
                    startCursor,
                    true,
                    getSize(),
                    getSort()
            );
        }
        return CursoredPageable.super.previous();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCursoredPageable that)) {
            return false;
        }
        return max == that.max &&
                Objects.equals(startCursor, that.startCursor) &&
                Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(max, startCursor, sort);
    }

    @Override
    public String toString() {
        return "DefaultCursoredPageable{" +
                "max=" + max +
                ", number=" + number +
                ", startCursor=" + startCursor +
                ", endCursor=" + endCursor +
                ", sort=" + sort +
                '}';
    }
}

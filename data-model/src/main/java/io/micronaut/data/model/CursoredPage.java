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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.data.model.Pageable.Cursor;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inspired by the Jakarta's {@code CursoredPage}, this models a type that supports
 * pagination operations with cursors.
 *
 * <p>A CursoredPage is a result set associated with a particular {@link Pageable} that includes
 * a calculation of the total size of page of records.</p>
 *
 * @param <T> The generic type
 * @author Andriy Dmytruk
 * @since 4.8.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeHint(CursoredPage.class)
@JsonDeserialize(as = DefaultCursoredPage.class)
@Serdeable
@DefaultImplementation(DefaultCursoredPage.class)
public interface CursoredPage<T> extends Page<T> {

    CursoredPage<?> EMPTY = new DefaultCursoredPage<>(Collections.emptyList(), Pageable.unpaged(), Collections.emptyList(), -1L);

    /**
     * @return Whether this {@link CursoredPage} contains the total count of the records
     * @since 4.8.0
     */
    boolean hasTotalSize();

    /**
     * Get the total count of all the records that can be given by this query.
     * The method may produce a {@link IllegalStateException} if the {@link Pageable} request
     * did not ask for total size.
     *
     * @return The total size of the all records.
     */
    long getTotalSize();

    /**
     * Get the total count of pages that can be given by this query.
     * The method may produce a {@link IllegalStateException} if the {@link Pageable} request
     * did not ask for total size.
     *
     * @return The total page of pages
     */
    default int getTotalPages() {
        int size = getSize();
        return size == 0 ? 1 : (int) Math.ceil((double) getTotalSize() / (double) size);
    }

    @Override
    default boolean hasNext() {
        Pageable pageable = getPageable();
        if (pageable.getMode() == Mode.CURSOR_NEXT) {
            return getContent().size() == pageable.getSize();
        } else {
            return true;
        }
    }

    @Override
    default boolean hasPrevious() {
        Pageable pageable = getPageable();
        if (pageable.getMode() == Mode.CURSOR_PREVIOUS) {
            return getContent().size() == pageable.getSize();
        } else {
            return true;
        }
    }

    @Override
    default CursoredPageable nextPageable() {
        Pageable pageable = getPageable();
        Cursor cursor = getCursor(getCursors().size() - 1).orElse(pageable.cursor().orElse(null));
        return Pageable.afterCursor(cursor, pageable.getNumber() + 1, pageable.getSize(), pageable.getSort());
    }

    @Override
    default CursoredPageable previousPageable() {
        Pageable pageable = getPageable();
        Cursor cursor = getCursor(0).orElse(pageable.cursor().orElse(null));
        return Pageable.beforeCursor(cursor, Math.max(0, pageable.getNumber() - 1), pageable.getSize(), pageable.getSort());
    }


    /**
     * Maps the content with the given function.
     *
     * @param function The function to apply to each element in the content.
     * @param <T2> The type returned by the function
     * @return A new slice with the mapped content
     */
    @Override
    default @NonNull <T2> CursoredPage<T2> map(Function<T, T2> function) {
        if (this == EMPTY) {
            return (CursoredPage<T2>) EMPTY;
        }
        List<T2> content = getContent().stream().map(function).collect(Collectors.toList());
        return new DefaultCursoredPage<>(content, getPageable(), getCursors(), hasTotalSize() ? getTotalSize() : null);
    }

    /**
     * Creates a cursored page from the given content, pageable, cursors and totalSize.
     *
     * @param content The content
     * @param pageable The pageable
     * @param cursors The cursors for cursored pagination
     * @param totalSize The total size
     * @param <T> The generic type
     * @return The slice
     */
    @JsonCreator
    @ReflectiveAccess
    static @NonNull <T> CursoredPage<T> of(
        @JsonProperty("content") @NonNull List<T> content,
        @JsonProperty("pageable") @NonNull Pageable pageable,
        @JsonProperty("cursors") @Nullable List<Cursor> cursors,
        @JsonProperty("totalSize") @Nullable Long totalSize
    ) {
        return new DefaultCursoredPage<>(content, pageable, cursors, totalSize);
    }

    /**
     * Get cursor at the given position or empty if no such cursor exists.
     * There must be a cursor for each of the data entities in the same order.
     * To start pagination after or before a cursor create a pageable from it using the
     * same sorting as before.
     *
     * @param i The index of cursor to retrieve.
     * @return The cursor at the provided index.
     */
    Optional<Cursor> getCursor(int i);

    /**
     * Get all the cursors.
     *
     * @see #getCursor(int) getCursor(i) for more details.
     * @return All the cursors
     */
    List<Cursor> getCursors();

    /**
     * Creates an empty page object.
     * @param <T2> The generic type
     * @return The slice
     */
    @SuppressWarnings("unchecked")
    static @NonNull <T2> CursoredPage<T2> empty() {
        return (CursoredPage<T2>) EMPTY;
    }

}

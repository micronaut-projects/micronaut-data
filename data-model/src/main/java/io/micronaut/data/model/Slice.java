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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inspired by the Spring Data's {@code Slice} and GORM's {@code PagedResultList}, this models a type that supports
 * pagination operations.
 *
 * <p>A slice is a result list associated with a particular {@link Pageable}</p>
 *
 * @param <T> The generic type
 * @author graemerocher
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public interface Slice<T> extends Iterable<T> {

    /**
     * @return The content.
     */
    @NonNull List<T> getContent();

    /**
     * @return The pageable for this slice.
     */
    @NonNull Pageable getPageable();

    /**
     * @return The page number
     */
    default int getPageNumber() {
        return getPageable().getNumber();
    }

    /**
     * @return The next pageable
     */
    default @NonNull Pageable nextPageable() {
        return getPageable().next();
    }

    /**
     * @return The previous pageable.
     */
    default @NonNull Pageable previousPageable() {
        return getPageable().previous();
    }

    /**
     * @return The offset.
     */
    default long getOffset() {
        return getPageable().getOffset();
    }

    /**
     * @return The size of the slice.
     */
    default int getSize() {
        return getPageable().getSize();
    }

    /**
     * @return Whether the slize is empty
     */
    default boolean isEmpty() {
        return getContent().isEmpty();
    }

    /**
     * @return The sort
     */
    @JsonIgnore
    default @NonNull Sort getSort() {
        return getPageable();
    }

    /**
     * @return The number of elements
     */
    default int getNumberOfElements() {
        return getContent().size();
    }

    @Override
    @NonNull
    default Iterator<T> iterator() {
        return getContent().iterator();
    }

    /**
     * Maps the content with the given function.
     *
     * @param function The function to apply to each element in the content.
     * @param <T2> The type returned by the function
     * @return A new slice with the mapped content
     */
    default @Nonnull <T2> Slice<T2> map(Function<T, T2> function) {
        List<T2> content = getContent().stream().map(function).collect(Collectors.toList());
        return new DefaultSlice<>(content, getPageable());
    }

    /**
     * Creates a slice from the given content and pageable.
     * @param content The content
     * @param pageable The pageable
     * @param <T2> The generic type
     * @return The slice
     */
    static @NonNull <T2> Slice<T2> of(@NonNull List<T2> content, @NonNull Pageable pageable) {
        return new DefaultSlice<>(content, pageable);
    }
}

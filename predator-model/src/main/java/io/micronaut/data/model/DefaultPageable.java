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
package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.validation.constraints.Min;

/**
 * The default pageable implementation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Introspected
final class DefaultPageable implements Pageable {

    private final int max;
    private final int number;
    private final Sort sort;

    /**
     * Default constructor.
     *
     * @param index The index
     * @param size The size
     * @param sort The sort
     */
    @Creator
    DefaultPageable(int index, int size, @Nullable Sort sort) {
        if (index < 0) {
            throw new IllegalArgumentException("Page index cannot be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Max size cannot be less than 1");
        }
        this.max = size;
        this.number = index;
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    @Override
    public @Min(1) int getSize() {
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
}

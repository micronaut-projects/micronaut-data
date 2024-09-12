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
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.data.model.Pageable.Cursor;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link Page} to return when {@link CursoredPageable} is requested.
 *
 * @author Andriy Dmytruk
 * @since 4.8.0
 * @param <T> The generic type
 */
@Serdeable
class DefaultCursoredPage<T> extends DefaultPage<T> implements CursoredPage<T> {

    private final List<Cursor> cursors;

    /**
     * Default constructor.
     * @param content The content
     * @param pageable The pageable
     * @param totalSize The total size
     */
    @JsonCreator
    @Creator
    @ReflectiveAccess
    DefaultCursoredPage(
            @JsonProperty("content")
            List<T> content,
            @JsonProperty("pageable")
            Pageable pageable,
            @JsonProperty("cursors")
            List<Cursor> cursors,
            @JsonProperty("totalSize")
            Long totalSize
    ) {
        super(content, pageable, totalSize);
        if (content.size() != cursors.size()) {
            throw new IllegalArgumentException("The number of cursors must match the number of content items for a page");
        }
        this.cursors = cursors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCursoredPage<?> that)) {
            return false;
        }
        return Objects.equals(cursors, that.cursors) && super.equals(o);
    }

    @Override
    public Optional<Cursor> getCursor(int i) {
        return i >= cursors.size() || i < 0 ? Optional.empty() : Optional.of(cursors.get(i));
    }

    @Override
    public List<Cursor> getCursors() {
        return cursors;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cursors, super.hashCode());
    }

    @Override
    public String toString() {
        return "DefaultPage{" +
                "totalSize=" + getTotalSize() +
                ",content=" + getContent() +
                ",pageable=" + getPageable() +
                ",cursors=" + cursors +
                '}';
    }
}

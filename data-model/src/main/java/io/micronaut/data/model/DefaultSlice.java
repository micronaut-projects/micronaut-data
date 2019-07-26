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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link Slice}.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <T> The generic type
 */
class DefaultSlice<T> implements Slice<T> {

    private final List<T> content;
    private final Pageable pageable;

    /**
     * Default constructor.
     * @param content The content
     * @param pageable The pageable
     */
    DefaultSlice(List<T> content, Pageable pageable) {
        ArgumentUtils.requireNonNull("pageable", pageable);
        this.content = CollectionUtils.isEmpty(content) ? Collections.emptyList() : content;
        this.pageable = pageable;
    }

    @NonNull
    @Override
    public List<T> getContent() {
        return content;
    }

    @NonNull
    @Override
    public Pageable getPageable() {
        return pageable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultSlice)) {
            return false;
        }
        DefaultSlice<?> that = (DefaultSlice<?>) o;
        return Objects.equals(content, that.content) &&
                Objects.equals(pageable, that.pageable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, pageable);
    }

    @Override
    public String toString() {
        return "DefaultSlice{" +
                "content=" + content +
                ", pageable=" + pageable +
                '}';
    }
}

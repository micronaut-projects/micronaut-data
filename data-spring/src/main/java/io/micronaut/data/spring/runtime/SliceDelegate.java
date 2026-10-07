/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.spring.runtime;

import io.micronaut.core.annotation.Internal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * Supports representing a Micronaut {@link io.micronaut.data.model.Slice} as a Spring Slice.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @param <T> The sliced type
 */
@Internal
final class SliceDelegate<T> implements Slice<T> {

    private final io.micronaut.data.model.Slice<T> delegate;

    /**
     * Default constructor.
     * @param delegate The object to delegate to
     */
    SliceDelegate(io.micronaut.data.model.Slice<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getNumber() {
        return delegate.getPageNumber();
    }

    @Override
    public int getSize() {
        return delegate.getPageable().isUnpaged() ? getNumberOfElements() : delegate.getSize();
    }

    @Override
    public int getNumberOfElements() {
        return delegate.getNumberOfElements();
    }

    @Override
    public <U> Slice<U> map(Function<? super T, ? extends U> converter) {
        return new SliceDelegate<>(
                io.micronaut.data.model.Slice.of(
                        getContent().stream().<U>map(converter).toList(),
                        delegate.getPageable()
                )
        );
    }

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public List<T> getContent() {
        return delegate.getContent();
    }

    @Override
    public boolean hasContent() {
        return !delegate.isEmpty();
    }

    @Override
    public Sort getSort() {
        return PageDelegate.toSort(delegate.getSort());
    }

    @Override
    public Pageable getPageable() {
        return delegate.getPageable().isUnpaged() ? Pageable.unpaged(getSort()) : Slice.super.getPageable();
    }

    @Override
    public boolean isFirst() {
        return !hasPrevious();
    }

    @Override
    public boolean isLast() {
        return !hasNext();
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public boolean hasPrevious() {
        return delegate.hasPrevious();
    }

    @Override
    public Pageable nextPageable() {
        return PageDelegate.toPageable(delegate.nextPageable());
    }

    @Override
    public Pageable previousPageable() {
        return PageDelegate.toPageable(delegate.previousPageable());
    }
}

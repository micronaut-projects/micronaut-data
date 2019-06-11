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
package io.micronaut.data.runtime.spring;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Supports representing a Micronaut {@link io.micronaut.data.model.Page} as a Spring Page.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <T> The paged type
 */
@Internal
class PageDelegate<T> implements Page<T> {

    private final io.micronaut.data.model.Page<T> delegate;

    /**
     * Default constructor.
     * @param delegate The object to delegate to
     */
    PageDelegate(io.micronaut.data.model.Page<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getNumber() {
        return delegate.getPageNumber();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    @Override
    public int getTotalPages() {
        return delegate.getTotalPages();
    }

    @Override
    public int getNumberOfElements() {
        return delegate.getNumberOfElements();
    }

    @Override
    public long getTotalElements() {
        return delegate.getTotalSize();
    }

    @Override
    public <U> Page<U> map(Function<? super T, ? extends U> converter) {
        return new PageDelegate<>(
                io.micronaut.data.model.Page.of(
                        getContent().stream().map(converter).collect(Collectors.toList()),
                        delegate.getPageable(),
                        delegate.getTotalSize()
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
        List<io.micronaut.data.model.Sort.Order> orderBy = delegate.getSort().getOrderBy();
        if (CollectionUtils.isEmpty(orderBy)) {
            return Sort.unsorted();
        } else {
            return new SortDelegate(delegate.getSort());
        }
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
        return getNumber() + 1 < getTotalPages();
    }

    @Override
    public boolean hasPrevious() {
        return getNumber() > 0;
    }

    @Override
    public Pageable nextPageable() {
        return new PageableDelegate(delegate.nextPageable());
    }

    @Override
    public Pageable previousPageable() {
        return new PageableDelegate(delegate.previousPageable());
    }

    /**
     * A pageable delegate impl.
     */
    private class PageableDelegate implements Pageable {

        private final io.micronaut.data.model.Pageable pageable;

        PageableDelegate(io.micronaut.data.model.Pageable pageable) {
            this.pageable = pageable;
        }

        @Override
        public int getPageNumber() {
            return pageable.getNumber();
        }

        @Override
        public int getPageSize() {
            return pageable.getSize();
        }

        @Override
        public long getOffset() {
            return pageable.getOffset();
        }

        @Override
        public Sort getSort() {
            return new SortDelegate(this.pageable.getSort());
        }

        @Override
        public Pageable next() {
            return new PageableDelegate(pageable.next());
        }

        @Override
        public Pageable previousOrFirst() {
            return new PageableDelegate(pageable.previous());
        }

        @Override
        public Pageable first() {
            return new PageableDelegate(
                    io.micronaut.data.model.Pageable.from(0, delegate.getSize(), delegate.getSort())
            );
        }

        @Override
        public boolean hasPrevious() {
            return this.pageable.getNumber() > 0;
        }
    }

    /**
     * A sort delegate impl.
     */
    private class SortDelegate extends Sort {

        private final io.micronaut.data.model.Sort delegate;

        SortDelegate(io.micronaut.data.model.Sort delegate) {
            super(Direction.ASC, "temp"); // not used in reality
            this.delegate = delegate;
        }

        @Override
        public Sort and(Sort sort) {
            for (Order order : sort) {
                delegate.order(
                        order.getProperty(),
                        io.micronaut.data.model.Sort.Order.Direction.valueOf(
                                order.getDirection().name()
                        )
                );
            }
            return this;
        }

        @Override
        public Order getOrderFor(String property) {
            return delegate.getOrderBy().stream().filter(o -> o.getProperty().equals(property))
                        .map(order ->
                                new Order(Direction.valueOf(order.getDirection().name()), order.getProperty()))
                        .findFirst().orElse(null);
        }

        @Override
        public Iterator<Order> iterator() {
            Iterator<io.micronaut.data.model.Sort.Order> i = delegate.getOrderBy().iterator();
            return new Iterator<Order>() {
                @Override
                public boolean hasNext() {
                    return i.hasNext();
                }

                @Override
                public Order next() {
                    io.micronaut.data.model.Sort.Order next = i.next();
                    return new Order(Direction.valueOf(next.getDirection().name()), next.getProperty());
                }
            };
        }
    }
}

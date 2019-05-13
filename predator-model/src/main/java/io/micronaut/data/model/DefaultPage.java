package io.micronaut.data.model;

import java.util.List;

/**
 * Default implementation of {@link Page}.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <T> The generic type
 */
class DefaultPage<T> extends DefaultSlice<T> implements Page<T> {

    private final long totalSize;

    DefaultPage(List<T> content, Pageable pageable, long totalSize) {
        super(content, pageable);
        this.totalSize = totalSize;
    }

    @Override
    public long getTotalSize() {
        return totalSize;
    }
}

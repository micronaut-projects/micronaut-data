package io.micronaut.data.model;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.model.query.Sort;

import javax.annotation.Nonnull;
import javax.validation.constraints.Min;

/**
 * The default pageable implementation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Introspected
class DefaultPageable implements Pageable {

    private final int max;
    private final long offset;
    private final Sort sort = Sort.unsorted();

    /**
     * Default constructor.
     *
     * @param max The max
     * @param offset The offset
     */
    DefaultPageable(int max, long offset) {
        this.max = max;
        this.offset = offset;
    }

    @Creator
    DefaultPageable() {
        this.max = -1;
        this.offset = 0;
    }

    @Override
    public @Min(1) int getSize() {
        return max;
    }

    @Override
    public @Min(0) long getOffset() {
        return offset;
    }

    @Nonnull
    @Override
    public Sort getSort() {
        return sort;
    }
}

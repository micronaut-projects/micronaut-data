package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.model.query.Sort;

import edu.umd.cs.findbugs.annotations.NonNull;
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
    private final int number;
    private final Sort sort;

    /**
     * Default constructor.
     *
     * @param index The index
     * @param size The size
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

    DefaultPageable(int index, int size) {
        this(index, size, Sort.unsorted());
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

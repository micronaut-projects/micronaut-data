package io.micronaut.data.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.model.query.Sort;

import javax.annotation.Nonnull;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * Pagination information.
 *
 * @author boros
 * @author graemerocher
 * @since 1.0.0
 */
@Introspected
public interface Pageable extends Sort {

    /**
     * Maximum size of the page to be returned. A value of -1 indicates no maximum.
     * @return size of the requested page of items
     */
    default int getSize() {
        return -1;
    }

    /**
     * Offset in the requested collection. Defaults to zero.
     * @return offset in the requested collection
     */
    @Min(0)
    default long getOffset() {
        return 0;
    }

    /**
     * @return The sort definition to use.
     */
    @Nonnull
    default Sort getSort() {
        return Sort.unsorted();
    }

    @Nonnull
    @Override
    default Pageable order(@Nonnull String propertyName) {
        getSort().order(propertyName);
        return this;
    }

    @Nonnull
    @Override
    default Pageable order(@Nonnull Order order) {
        getSort().order(order);
        return this;
    }

    @Nonnull
    @Override
    default Pageable order(@Nonnull String propertyName, @Nonnull Order.Direction direction) {
        getSort().order(propertyName, direction);
        return this;
    }

    @Nonnull
    @Override
    default List<Order> getOrderBy() {
        return getSort().getOrderBy();
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param offset The offset
     * @return The pageable
     */
    static @Nonnull Pageable from(long offset) {
        return new DefaultPageable(-1, offset);
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param offset The offset
     * @param max the max
     * @return The pageable
     */
    static @Nonnull Pageable from(long offset, int max) {
        return new DefaultPageable(max, offset);
    }

    /**
     * @return A new instance without paging data.
     */
    static @Nonnull Pageable unpaged() {
        return new DefaultPageable(-1, 0);
    }
}
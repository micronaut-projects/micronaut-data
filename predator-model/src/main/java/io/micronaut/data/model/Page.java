package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Inspired by the Spring Data's {@code Page} and GORM's {@code PagedResultList}, this models a type that supports
 * pagination operations.
 *
 * <p>A Page is a result set associated with a particular {@link Pageable} that includes a calculation of the total
 * size of number of records.</p>
 *
 * @param <T> The generic type
 * @author graemerocher
 * @since 1.0.0
 */
public interface Page<T> extends Slice<T> {

    /**
     * @return The total size of the all records.
     */
    long getTotalSize();

    /**
     * @return The total number of pages
     */
    default int getTotalPages() {
        int size = getSize();
        return size == 0 ? 1 : (int) Math.ceil((double) getTotalSize() / (double) size);
    }

    /**
     * Creates a slice from the given content and pageable.
     * @param content The content
     * @param pageable The pageable
     * @param totalSize The total size
     * @param <T2> The generic type
     * @return The slice
     */
    static @NonNull <T2> Page<T2> of(@NonNull List<T2> content, @NonNull Pageable pageable, long totalSize) {
        return new DefaultPage<>(content, pageable, totalSize);
    }

    /**
     * Creates an empty page object.
     * @param <T2> The generic type
     * @return The slice
     */
    static @NonNull <T2> Page<T2> empty() {
        return new DefaultPage<>(Collections.emptyList(), Pageable.unpaged(), 0);
    }
}

package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

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
}

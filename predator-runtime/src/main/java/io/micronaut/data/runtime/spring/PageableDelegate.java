package io.micronaut.data.runtime.spring;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.Sort;

/**
 * Supports representing a Spring Pageable as a Micronaut {@link Pageable}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class PageableDelegate implements Pageable {

    private final org.springframework.data.domain.Pageable target;

    PageableDelegate(org.springframework.data.domain.Pageable target) {
        this.target = target;
    }

    @Override
    public int getNumber() {
        return target.getPageNumber();
    }

    @Override
    public int getSize() {
        return target.getPageSize();
    }

    @Override
    public long getOffset() {
        return target.getOffset();
    }

    @NonNull
    @Override
    public Sort getSort() {
        return new SortDelegate(target.getSort());
    }
}

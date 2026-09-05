package io.micronaut.data.nitrite.repository

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.Limit
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.nitrite.model.CriteriaPerson

/**
 * Paged query over {@link CriteriaPerson}, used to call the paging operations directly rather
 * than through a repository method.
 */
class CriteriaPersonPagedQuery implements PagedQuery<CriteriaPerson> {

    private final Pageable pageable

    CriteriaPersonPagedQuery(Pageable pageable) {
        this.pageable = pageable
    }

    @Override
    Class<CriteriaPerson> getRootEntity() {
        return CriteriaPerson
    }

    @Override
    Pageable getPageable() {
        return pageable
    }

    @Override
    Limit getQueryLimit() {
        return Limit.of(100, 0)
    }

    @Override
    String getName() {
        return CriteriaPerson.simpleName
    }

    @Override
    AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA
    }
}

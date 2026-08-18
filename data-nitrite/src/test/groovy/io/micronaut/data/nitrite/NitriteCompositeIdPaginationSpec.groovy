package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.nitrite.model.CompositePageEntity
import io.micronaut.data.nitrite.repository.CompositePageEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Paging over rows that share one sort value needs a tie-breaker, otherwise a row can be skipped
 * between pages. The identity is that tie-breaker, including when it is composite.
 *
 * <p>This guards the outcome rather than the mechanism: Nitrite's natural order is stable for this
 * data, so the assertion also held before the identity joined the sort. It fails if a future change
 * makes the page boundary depend on the non-unique sort value alone.
 */
class NitriteCompositeIdPaginationSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])

    @Shared
    CompositePageEntityRepository repository = context.getBean(CompositePageEntityRepository)

    def setup() {
        repository.deleteAll()
    }

    void "pages sorted by a non-unique field return every record of a composite-id entity"() {
        given: "four rows sharing one sort value, so the sort alone cannot order them"
        4.times { i ->
            repository.save(new CompositePageEntity("shard", "seq-$i", "same", "payload-$i"))
        }

        when: "the rows are read one page at a time, sorted only by that non-unique field"
        Sort sort = Sort.of(Sort.Order.asc("sortKey"))
        def firstPage = repository.findAll(Pageable.from(0, 2, sort))
        def secondPage = repository.findAll(Pageable.from(1, 2, sort))

        then: "the composite identity breaks the tie, so no row is skipped or repeated"
        (firstPage + secondPage)*.payload.toSet() == ["payload-0", "payload-1", "payload-2", "payload-3"].toSet()
    }
}

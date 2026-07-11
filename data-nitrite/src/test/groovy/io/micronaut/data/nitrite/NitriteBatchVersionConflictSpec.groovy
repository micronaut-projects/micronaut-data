package io.micronaut.data.nitrite

import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.nitrite.model.VersionedRecord
import io.micronaut.data.nitrite.repository.VersionedRecordRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Regression test for the batch update path in NitriteEntitiesOperations#execute
 * (hottest PROD_RISK method per hotpath analysis, 68% branch coverage): the updateAll()
 * optimistic-lock conflict branch.
 *
 * Note: the `insert=true`, id-already-set "upsert" branch in the same method (lines ~274-294)
 * turned out NOT to be reachable through the public saveAll() repository method — Micronaut
 * Data's DefaultSaveAllInterceptor splits a batch by id-presence before calling into this
 * module at all, routing pre-assigned-id entities to updateAll() instead (confirmed by a
 * discarded test here: saving an unpersisted entity with a manually-assigned id and version
 * via saveAll() threw OptimisticLockException from *updateAll*, not the insert path). That
 * branch may be dead code reachable only via a direct, non-interceptor call to persistAll();
 * flagged for a follow-up dead-code check rather than force-testing an unreachable path.
 */
@MicronautTest(transactional = false)
class NitriteBatchVersionConflictSpec extends Specification {

    @Inject
    VersionedRecordRepository repo

    def setup() {
        repo.deleteAll()
    }

    void "updateAll throws OptimisticLockException when a record's version is stale"() {
        given: "a record updated once via updateAll, moving its version to 1"
            def record = repo.save(new VersionedRecord("initial"))
            record.name = "first-update"
            repo.updateAll([record]).toList()

        and: "a stale local copy still carrying the original version (0)"
            def stale = new VersionedRecord("stale-update")
            stale.id = record.id
            stale.version = 0L

        when:
            repo.updateAll([stale]).toList()

        then:
            thrown(OptimisticLockException)

        and: "the stored record is unaffected by the failed conflicting update"
            repo.findById(record.id).orElseThrow().name == "first-update"
    }
}

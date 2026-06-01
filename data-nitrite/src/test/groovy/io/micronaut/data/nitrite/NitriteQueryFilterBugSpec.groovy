package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.UuidTestEntity
import io.micronaut.data.nitrite.repository.UuidTestRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Test for @Query field filter with camelCase field names in Nitrite.
 *
 * Verifies that @Query JSON filters correctly handle field naming strategy:
 * - Java field: canonicalName (camelCase)
 * - Stored field: canonical_name (snake_case)
 *
 * This test ensures the fix for snake_case field name conversion in @Query filters works.
 */
@MicronautTest(transactional = false)
class NitriteQueryFilterBugSpec extends Specification {

    @Inject
    UuidTestRepository repo

    def setup() {
        repo.deleteAll()
    }

    void "test @Query field filter works for UUID strings"() {
        given:
            def id = UUID.randomUUID()
            def canonicalName = "test-" + id  // UUID string like "test-5ce5db26-6cb5-4f88-9a61-3d324c0cfc1b"

            def entity = new UuidTestEntity(id, canonicalName)
            repo.save(entity)

        when: "Using @Query with field filter"
            def byQuery = repo.findByCanonicalName(canonicalName)

        and: "Using findAll() + stream filter"
            def byFindAll = repo.findAll().stream()
                .filter({ e -> e.canonicalName == canonicalName })
                .findFirst()
                .orElse(null)

        then: "Both @Query and findAll() should work"
            byQuery.isPresent()
            byQuery.get().canonicalName == canonicalName
            byFindAll != null
            byFindAll.canonicalName == canonicalName
    }
}

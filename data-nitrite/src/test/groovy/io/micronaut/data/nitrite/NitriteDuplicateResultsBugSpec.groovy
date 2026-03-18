package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.DuplicateTestEntity
import io.micronaut.data.nitrite.repository.DuplicateTestRepository
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Regression test for MVSTORE duplicate results bug.
 *
 * Previously, when a repository explicitly declared List<T> findAll() with @Query("{}"),
 * Micronaut Data generated an implementation that executed alongside the inherited
 * CrudRepository.findAll(), causing duplicate results in MVSTORE mode.
 *
 * This test verifies that findAll() returns the correct count (no duplicates).
 */
class NitriteDuplicateResultsBugSpec extends Specification {

    def "test findAll does not return duplicate results in MVSTORE mode"() {
        given: "MVSTORE configuration with db in target folder"
            Files.createDirectories(Paths.get("target/test-db"))
            def props = [
                "nitrite.storage-mode": "MVSTORE",
                "nitrite.db-path": "target/test-db/duplicate-test.db"
            ]
            def ctx = ApplicationContext.run(props)
            def repo = ctx.getBean(DuplicateTestRepository)
            repo.deleteAll()

            // Save 3 distinct entities
            def entity1 = new DuplicateTestEntity(UUID.randomUUID(), "Entity-1")
            def entity2 = new DuplicateTestEntity(UUID.randomUUID(), "Entity-2")
            def entity3 = new DuplicateTestEntity(UUID.randomUUID(), "Entity-3")
            repo.saveAll([entity1, entity2, entity3] as List<DuplicateTestEntity>)

        when: "Calling findAll()"
            def results = repo.findAll() as List

        then: "Should return exactly 3 results (no duplicates)"
            results.size() == 3
            results.collect { it.name }.toSet() == ["Entity-1", "Entity-2", "Entity-3"] as Set

        cleanup:
            ctx.close()
    }

    def "test findByName does not return duplicate results in MVSTORE mode"() {
        given: "MVSTORE configuration"
            Files.createDirectories(Paths.get("target/test-db"))
            def props = [
                "nitrite.storage-mode": "MVSTORE",
                "nitrite.db-path": "target/test-db/duplicate-test2.db"
            ]
            def ctx = ApplicationContext.run(props)
            def repo = ctx.getBean(DuplicateTestRepository)
            repo.deleteAll()

            def name = "Unique-" + UUID.randomUUID()
            def entity = new DuplicateTestEntity(UUID.randomUUID(), name)
            repo.save(entity)

        when: "Calling findByName"
            def results = repo.findByName(name) as List

        then: "Should return exactly 1 result"
            results.size() == 1
            results[0].name == name

        cleanup:
            ctx.close()
    }

    def "test save on existing record does not create duplicate in MVSTORE mode"() {
        given: "MVSTORE configuration"
            Files.createDirectories(Paths.get("target/test-db"))
            def props = [
                "nitrite.storage-mode": "MVSTORE",
                "nitrite.db-path": "target/test-db/duplicate-test3.db"
            ]
            def ctx = ApplicationContext.run(props)
            def repo = ctx.getBean(DuplicateTestRepository)
            repo.deleteAll()

            def id = UUID.randomUUID()
            def original = new DuplicateTestEntity(id, "save-update-test-" + id)
            repo.save(original)

        when: "Reload and update sessionCount"
            def reloaded = repo.findAll().stream()
                .filter { it.id == id }
                .findFirst()
                .orElse(null)

            reloaded.name = "updated-name"
            repo.save(reloaded)

            def count = repo.findAll().stream()
                .filter { it.id == id }
                .count()

        then: "Should have exactly 1 record, not 2 (no duplicate)"
            count == 1

        cleanup:
            ctx.close()
    }
}

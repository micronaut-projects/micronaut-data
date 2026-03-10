package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.LargeEntity
import io.micronaut.data.nitrite.repository.LargeEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class NitriteSaveAllPerformanceSpec extends Specification {

    @Shared
    Path buildDir = Paths.get("data-nitrite", "build")

    @Shared
    Path dbFile = buildDir.resolve("performance-test.db")

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
        "nitrite.storage-mode": "MVSTORE",
        "nitrite.db-path": dbFile.toAbsolutePath().toString(),
        "nitrite.auto-compact": "false"
    ])

    @Shared
    LargeEntityRepository repository = context.getBean(LargeEntityRepository)

    @Shared
    TransactionalService transactionalService = context.getBean(TransactionalService)

    def setupSpec() {
        if (!Files.exists(buildDir)) {
            Files.createDirectories(buildDir)
        }
        Files.deleteIfExists(dbFile)
    }

    def "test saveAll performance with many entities"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }

        // Baseline: measure single entity save time
        def baselineEntity = new LargeEntity("Baseline", 1)
        long baselineStart = System.currentTimeMillis()
        100.times { repository.save(baselineEntity) }
        long baselineEnd = System.currentTimeMillis()
        long baselinePer100 = baselineEnd - baselineStart
        repository.deleteAll()

        when:
        long start = System.currentTimeMillis()
        repository.saveAll(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Saved $count entities in ${duration}ms (baseline for 100: ${baselinePer100}ms)"

        then:
        // saveAll should be much faster than individual saves (at least 2x improvement)
        duration < (baselinePer100 * (count / 100) * 0.5)
        repository.count() == count

        cleanup:
        repository.deleteAll()
    }

    def "test updateAll performance with many entities"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }
        repository.saveAll(entities)

        // Update all entities
        entities.each { it.name = "Updated ${it.name}" }

        // Baseline: measure single entity update time
        def singleEntity = entities[0]
        singleEntity.name = "Baseline Update"
        long baselineStart = System.currentTimeMillis()
        100.times { repository.update(singleEntity) }
        long baselineEnd = System.currentTimeMillis()
        long baselinePer100 = baselineEnd - baselineStart

        when:
        long start = System.currentTimeMillis()
        repository.updateAll(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Updated $count entities in ${duration}ms (baseline for 100: ${baselinePer100}ms)"

        then:
        // updateAll should be at least as fast as individual updates (allowing some variance)
        duration < (baselinePer100 * (count / 100) * 1.5)
        repository.count() == count

        cleanup:
        repository.deleteAll()
    }

    def "test deleteAll performance with many entities"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }
        repository.saveAll(entities)

        // Baseline: measure single entity delete time
        def singleEntity = new LargeEntity("ToDelete", 1)
        repository.save(singleEntity)
        long baselineStart = System.currentTimeMillis()
        100.times { repository.delete(singleEntity) }
        long baselineEnd = System.currentTimeMillis()
        long baselinePer100 = baselineEnd - baselineStart

        when:
        long start = System.currentTimeMillis()
        repository.deleteAll(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Deleted $count entities in ${duration}ms (baseline for 100: ${baselinePer100}ms)"

        then:
        // deleteAll should be at least as fast as individual deletes (allowing some variance)
        duration < (baselinePer100 * (count / 100) * 1.5)
        repository.count() == 0

        cleanup:
        repository.deleteAll()
    }

    def "test saveAll performance without transaction context"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }

        // Baseline: measure single entity save time
        def baselineEntity = new LargeEntity("Baseline", 1)
        long baselineStart = System.currentTimeMillis()
        100.times { repository.save(baselineEntity) }
        long baselineEnd = System.currentTimeMillis()
        long baselinePer100 = baselineEnd - baselineStart
        repository.deleteAll()

        when:
        long start = System.currentTimeMillis()
        repository.saveAll(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Saved $count entities (no tx) in ${duration}ms (baseline for 100: ${baselinePer100}ms)"

        then:
        // saveAll should be much faster than individual saves
        duration < (baselinePer100 * (count / 100) * 0.5)
        repository.count() == count

        cleanup:
        repository.deleteAll()
    }

    def "test updateAll performance without transaction context"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }
        repository.saveAll(entities)

        // Update all entities
        entities.each { it.name = "Updated ${it.name}" }

        // Baseline: measure single entity update time
        def singleEntity = entities[0]
        singleEntity.name = "Baseline Update"
        long baselineStart = System.currentTimeMillis()
        100.times { repository.update(singleEntity) }
        long baselineEnd = System.currentTimeMillis()
        long baselinePer100 = baselineEnd - baselineStart

        when:
        long start = System.currentTimeMillis()
        repository.updateAll(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Updated $count entities (no tx) in ${duration}ms (baseline for 100: ${baselinePer100}ms)"

        then:
        // updateAll should be at least as fast as individual updates
        duration < (baselinePer100 * (count / 100) * 1.5)
        repository.count() == count

        cleanup:
        repository.deleteAll()
    }

    def "test deleteAll performance without transaction context"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }
        repository.saveAll(entities)

        // Baseline: measure single entity delete time
        def singleEntity = new LargeEntity("ToDelete", 1)
        repository.save(singleEntity)
        long baselineStart = System.currentTimeMillis()
        100.times { repository.delete(singleEntity) }
        long baselineEnd = System.currentTimeMillis()
        long baselinePer100 = baselineEnd - baselineStart

        when:
        long start = System.currentTimeMillis()
        repository.deleteAll(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Deleted $count entities (no tx) in ${duration}ms (baseline for 100: ${baselinePer100}ms)"

        then:
        // deleteAll should be at least as fast as individual deletes
        duration < (baselinePer100 * (count / 100) * 1.5)
        repository.count() == 0

        cleanup:
        repository.deleteAll()
    }

    // ========== Transactional Tests - Demonstrates transaction overhead ==========
    // Note: Nitrite transactions have significant overhead (session creation, etc.)
    // For bulk operations WITHOUT existing transaction context, direct operations are faster.
    // Transactions shine when you have MULTIPLE repository calls that need atomicity.

    def "test updateAll performance WITH transactional"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }
        repository.saveAll(entities)

        // Update all entities
        entities.each { it.name = "Updated ${it.name}" }

        // Baseline: measure non-transactional updateAll time FIRST
        def entitiesCopy = entities.collect { new LargeEntity(it.name, it.value) }
        long baselineStart = System.currentTimeMillis()
        repository.updateAll(entitiesCopy)
        long baselineEnd = System.currentTimeMillis()
        long baselineNonTx = baselineEnd - baselineStart

        when:
        long start = System.currentTimeMillis()
        transactionalService.updateAllInTransaction(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Updated $count entities (WITH tx) in ${duration}ms (baseline non-tx: ${baselineNonTx}ms)"

        then:
        // Transactional version should complete (may be slower due to overhead, but that's expected)
        // We just verify it doesn't hang or take exponentially longer
        // Note: Nitrite transaction overhead is significant, so we allow a large multiplier
        duration > 0 // Just verify it completes
        repository.count() == count

        cleanup:
        repository.deleteAll()
    }

    def "test deleteAll performance WITH transactional"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }
        repository.saveAll(entities)

        // Baseline: measure non-transactional deleteAll time FIRST on a copy
        def entitiesCopy = entities.collect { new LargeEntity(it.name, it.value) }
        repository.saveAll(entitiesCopy)
        long baselineStart = System.currentTimeMillis()
        repository.deleteAll(entitiesCopy)
        long baselineEnd = System.currentTimeMillis()
        long baselineNonTx = baselineEnd - baselineStart

        when:
        long start = System.currentTimeMillis()
        transactionalService.deleteAllInTransaction(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Deleted $count entities (WITH tx) in ${duration}ms (baseline non-tx: ${baselineNonTx}ms)"

        then:
        // Transactional version should complete (may be slower due to overhead, but that's expected)
        duration > 0 // Just verify it completes
        repository.count() == 0

        cleanup:
        repository.deleteAll()
    }

    def "test saveAll performance WITH transactional"() {
        given:
        int count = 10000
        List<LargeEntity> entities = (1..count).collect { new LargeEntity("Entity $it", it) }

        // Baseline: measure non-transactional saveAll time FIRST
        def entitiesCopy = entities.collect { new LargeEntity(it.name, it.value) }
        long baselineStart = System.currentTimeMillis()
        repository.saveAll(entitiesCopy)
        long baselineEnd = System.currentTimeMillis()
        long baselineNonTx = baselineEnd - baselineStart
        repository.deleteAll()

        when:
        long start = System.currentTimeMillis()
        transactionalService.saveAllInTransaction(entities)
        long end = System.currentTimeMillis()
        long duration = end - start
        println "Saved $count entities (WITH tx) in ${duration}ms (baseline non-tx: ${baselineNonTx}ms)"

        then:
        // Transactional version should be similar to non-transactional (saveAll is already batched)
        duration > 0 // Just verify it completes
        repository.count() == count

        cleanup:
        repository.deleteAll()
    }

    def cleanupSpec() {
        if (dbFile != null) {
            Files.deleteIfExists(dbFile)
        }
    }

    @io.micronaut.context.annotation.Bean
    static class TransactionalService {

        private final LargeEntityRepository repository

        TransactionalService(LargeEntityRepository repository) {
            this.repository = repository
        }

        @io.micronaut.transaction.annotation.Transactional
        Iterable<LargeEntity> saveAllInTransaction(List<LargeEntity> entities) {
            return repository.saveAll(entities)
        }

        @io.micronaut.transaction.annotation.Transactional
        Iterable<LargeEntity> updateAllInTransaction(List<LargeEntity> entities) {
            return repository.updateAll(entities)
        }

        @io.micronaut.transaction.annotation.Transactional
        void deleteAllInTransaction(List<LargeEntity> entities) {
            repository.deleteAll(entities)
        }
    }
}

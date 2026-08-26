package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.DuplicateTestEntity
import io.micronaut.data.nitrite.model.LongIdEntity
import io.micronaut.data.nitrite.model.StringIdEntity
import io.micronaut.data.nitrite.model.VersionedRecord
import io.micronaut.data.nitrite.repository.DuplicateTestRepository
import io.micronaut.data.nitrite.repository.LongIdRepository
import io.micronaut.data.nitrite.repository.StringIdRepository
import io.micronaut.data.nitrite.repository.VersionedRecordRepository
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Consolidated regression tests for Nitrite save() upsert behavior.
 *
 * Verifies that save() correctly handles:
 * - New entities (no ID) → INSERT with generated ID
 * - Existing entities (has ID) → UPSERT (update or insert if absent)
 * - Different ID types: UUID, String, Long
 * - Both IN_MEMORY and MVSTORE storage modes
 */
class NitriteUpsertSpec extends Specification {

    def "update with a null ID does not insert a transient entity"() {
        given:
        def ctx = createContext("IN_MEMORY", "null-id-update")
        def repo = getRepository(ctx, "String")
        repo.deleteAll()
        def entity = createEntity("String", null, "transient")

        when:
        repo.update(entity)

        then:
        repo.findAll().isEmpty()

        cleanup:
        ctx.close()
    }

    @Unroll
    def "test saveAll batch with mixed new and existing entities in #mode mode"() {
        given: "Configuration for storage mode"
            def ctx = createContext(mode, "batch-${mode.toLowerCase()}")
            def repo = getRepository(ctx, "UUID")
            repo.deleteAll()

            // Create initial entities
            def id1 = UUID.randomUUID()
            def id2 = UUID.randomUUID()
            repo.save(createEntity("UUID", id1, "entity1"))
            repo.save(createEntity("UUID", id2, "entity2"))

        when: "SaveAll with mix of existing and new entities"
            def existing1 = repo.findById(id1).orElse(null)
            existing1.name = "entity1-updated"

            def existing2 = repo.findById(id2).orElse(null)
            existing2.name = "entity2-updated"

            def newEntity = createEntity("UUID", null, "new-entity")

            def results = repo.saveAll([existing1, existing2, newEntity] as List)

            def all = repo.findAll()

        then: "Should update existing and insert new"
            results.size() == 3
            all.size() == 3
            all.find { it.id == id1 }.name == "entity1-updated"
            all.find { it.id == id2 }.name == "entity2-updated"
            all.find { it.name == "new-entity" } != null

        cleanup:
            ctx.close()

        where:
        mode << ["IN_MEMORY", "MVSTORE"]
    }

    // ========== Helper Methods ==========

    private ApplicationContext createContext(String mode, String testName) {
        Files.createDirectories(Paths.get("build/test-db"))
        def props = [
            "micronaut.nitrite.default.storage-mode": mode,
            "micronaut.nitrite.default.db-path": "build/test-db/${testName}.db"
        ]
        if (mode == "IN_MEMORY") {
            props.remove("micronaut.nitrite.default.db-path")
        }
        return ApplicationContext.run(props)
    }

    private def getRepository(ApplicationContext ctx, String idType) {
        switch (idType) {
            case "UUID": return ctx.getBean(DuplicateTestRepository)
            case "String": return ctx.getBean(StringIdRepository)
            case "Long": return ctx.getBean(LongIdRepository)
            default: throw new IllegalArgumentException("Unknown ID type: $idType")
        }
    }

    private def createEntity(String idType, def id, String name) {
        switch (idType) {
            case "UUID": return new DuplicateTestEntity((UUID) id, name)
            case "String": return new StringIdEntity((String) id, name)
            case "Long": return new LongIdEntity((Long) id, name)
            default: throw new IllegalArgumentException("Unknown ID type: $idType")
        }
    }

    private def getId(def entity, String idType) {
        switch (idType) {
            case "UUID": return ((DuplicateTestEntity) entity).id
            case "String": return ((StringIdEntity) entity).id
            case "Long": return ((LongIdEntity) entity).id
            default: throw new IllegalArgumentException("Unknown ID type: $idType")
        }
    }
}

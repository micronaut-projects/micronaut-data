package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.*
import io.micronaut.data.nitrite.repository.*
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

    @Unroll
    def "test save on new entity with #idType ID in #mode mode"() {
        given: "Configuration for storage mode"
            def ctx = createContext(mode, "new-${idType.toLowerCase()}-${mode.toLowerCase()}")
            def repo = getRepository(ctx, idType)
            repo.deleteAll()

        when: "Save a new entity without ID"
            def entity = createEntity(idType, null, "new-entity")
            def saved = repo.save(entity)
            def id = getId(saved, idType)

        then: "Should generate ID and persist"
            id != null
            repo.findById(id).isPresent()
            repo.findById(id).get().name == "new-entity"
            repo.findAll().size() == 1

        cleanup:
            ctx.close()

        where:
        mode << ["IN_MEMORY", "IN_MEMORY", "IN_MEMORY", "MVSTORE", "MVSTORE", "MVSTORE"]
        idType << ["UUID", "String", "Long", "UUID", "String", "Long"]
    }

    @Unroll
    def "test save on existing entity does not create duplicate with #idType ID in #mode mode"() {
        given: "Configuration for storage mode"
            def ctx = createContext(mode, "update-${idType.toLowerCase()}-${mode.toLowerCase()}")
            def repo = getRepository(ctx, idType)
            repo.deleteAll()

            def id = idType == "UUID" ? UUID.randomUUID() :
                      idType == "String" ? "test-id-" + System.currentTimeMillis() :
                      1L
            def original = createEntity(idType, id, "original")
            repo.save(original)

        when: "Reload and save with modified data"
            def initialCount = repo.findAll().size()
            def reloaded = repo.findById(id).orElse(null)

            reloaded.name = "updated"
            repo.save(reloaded)

            def finalCount = repo.findAll().size()
            def allById = repo.findAll().findAll { getId(it, idType) == id }

        then: "Should update, not create duplicate"
            initialCount == 1
            finalCount == 1
            allById.size() == 1
            allById[0].name == "updated"

        cleanup:
            ctx.close()

        where:
        mode << ["IN_MEMORY", "IN_MEMORY", "IN_MEMORY", "MVSTORE", "MVSTORE", "MVSTORE"]
        idType << ["UUID", "String", "Long", "UUID", "String", "Long"]
    }

    @Unroll
    def "test update method works correctly with #idType ID in #mode mode"() {
        given: "Configuration for storage mode"
            def ctx = createContext(mode, "update-method-${idType.toLowerCase()}-${mode.toLowerCase()}")
            def repo = getRepository(ctx, idType)
            repo.deleteAll()

            def id = idType == "UUID" ? UUID.randomUUID() :
                      idType == "String" ? "test-id-" + System.currentTimeMillis() :
                      1L
            def original = createEntity(idType, id, "original")
            repo.save(original)

        when: "Use update() method"
            def initialCount = repo.findAll().size()
            def reloaded = repo.findById(id).orElse(null)

            reloaded.name = "updated-via-update"
            repo.update(reloaded)

            def finalCount = repo.findAll().size()
            def allById = repo.findAll().findAll { getId(it, idType) == id }

        then: "update() should work correctly"
            initialCount == 1
            finalCount == 1
            allById.size() == 1
            allById[0].name == "updated-via-update"

        cleanup:
            ctx.close()

        where:
        mode << ["IN_MEMORY", "IN_MEMORY", "IN_MEMORY", "MVSTORE", "MVSTORE", "MVSTORE"]
        idType << ["UUID", "String", "Long", "UUID", "String", "Long"]
    }

    @Unroll
    def "test save after delete with #idType ID in #mode mode"() {
        given: "Configuration for storage mode"
            def ctx = createContext(mode, "save-delete-${idType.toLowerCase()}-${mode.toLowerCase()}")
            def repo = getRepository(ctx, idType)
            repo.deleteAll()

            def id = idType == "UUID" ? UUID.randomUUID() :
                      idType == "String" ? "test-id-" + System.currentTimeMillis() :
                      1L
            def original = createEntity(idType, id, "original")
            repo.save(original)

        when: "Delete then save with same ID"
            repo.delete(original)
            def afterDeleteCount = repo.findAll().size()

            def updated = createEntity(idType, id, "updated")
            repo.save(updated)

            def finalCount = repo.findAll().size()
            def allById = repo.findAll().findAll { getId(it, idType) == id }

        then: "Save after delete should create exactly 1 record"
            afterDeleteCount == 0
            finalCount == 1
            allById.size() == 1
            allById[0].name == "updated"

        cleanup:
            ctx.close()

        where:
        mode << ["IN_MEMORY", "IN_MEMORY", "IN_MEMORY", "MVSTORE", "MVSTORE", "MVSTORE"]
        idType << ["UUID", "String", "Long", "UUID", "String", "Long"]
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
        Files.createDirectories(Paths.get("target/test-db"))
        def props = [
            "nitrite.storage-mode": mode,
            "nitrite.db-path": "target/test-db/${testName}.db"
        ]
        if (mode == "IN_MEMORY") {
            props.remove("nitrite.db-path")
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

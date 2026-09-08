package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.DuplicateTestEntity
import io.micronaut.data.nitrite.model.LongIdEntity
import io.micronaut.data.nitrite.model.StringIdEntity
import io.micronaut.data.nitrite.repository.DuplicateTestRepository
import io.micronaut.data.nitrite.repository.LongIdRepository
import io.micronaut.data.nitrite.repository.StringIdRepository
import io.micronaut.data.nitrite.runtime.NitriteOperationsHelper
import org.dizitart.no2.collection.Document
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

/**
 * Consolidated regression tests for Nitrite Jakarta Data save behavior.
 *
 * Verifies that save() correctly handles:
 * - New entities (no ID) → INSERT with generated ID
 * - Existing entities (has ID) → UPDATE
 * - Assigned IDs that are not present → INSERT
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

    def "an entity stored without the identity as its document key is still found by id"() {
        given: "a document written the way a store predating the document-key optimisation holds it"
        def ctx = createContext("IN_MEMORY", "foreign-document-key")
        def repo = ctx.getBean(LongIdRepository)
        def helper = ctx.getBean(NitriteOperationsHelper)
        repo.deleteAll()

        def stored = Document.createDocument("id", 7L).put("name", "written-elsewhere")
        helper.getCollection(LongIdEntity).insert(stored)

        expect: "its key is Nitrite's own, unrelated to the identity"
        stored.getId().getIdValue() != 7L

        when: "it is looked up by that identity"
        def found = repo.findById(7L)

        then: "the lookup does not depend on the identity having been used as the key"
        found.present
        found.get().name == "written-elsewhere"

        when: "the same document is updated through its identity"
        def toUpdate = found.get()
        toUpdate.name = "updated"
        repo.update(toUpdate)

        then: "the update lands on it rather than inserting a second document"
        repo.count() == 1
        repo.findById(7L).get().name == "updated"

        when: "and deleted through its identity"
        repo.deleteById(7L)

        then:
        repo.findById(7L).isEmpty()
        repo.count() == 0

        cleanup:
        ctx.close()
    }

    private ApplicationContext createContext(String mode, String testName) {
        Files.createDirectories(Path.of("build/test-db"))
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
}

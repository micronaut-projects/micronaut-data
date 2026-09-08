package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.operations.NitriteIndexManagement
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteCollection
import org.dizitart.no2.index.IndexOptions
import org.dizitart.no2.index.IndexType
import spock.lang.Specification

import static org.dizitart.no2.index.IndexOptions.indexOptions

class NitriteIndexManagementSpec extends Specification {

    void "management is exposed as a per-datasource bean and rebuilds a unique index"() {
        given:
        def context = ApplicationContext.run([
                "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
        ])
        Nitrite database = context.getBean(Nitrite)
        NitriteIndexManagement management = context.getBean(NitriteIndexManagement)
        def collection = database.getCollection("bodies")
        collection.insert(
                Document.createDocument("id", "one"),
                Document.createDocument("id", "two"))
        collection.createIndex(indexOptions(IndexType.UNIQUE), "id")

        when:
        management.dropIndex("bodies", "id")

        then:
        !collection.hasIndex("id")

        when:
        management.rebuildUniqueIndex("bodies", "id")

        then:
        collection.hasIndex("id")

        when:
        try (def ignored = management.dropUniqueIndex("bodies", "id")) {
            assert !collection.hasIndex("id")
        }

        then:
        collection.hasIndex("id")

        when:
        collection.insert(Document.createDocument("id", "one"))

        then:
        thrown(RuntimeException)

        cleanup:
        context?.close()
    }

    void "a scope over an unindexed field is a no-op and closing twice rebuilds once"() {
        given:
        boolean present = false
        int rebuilds = 0
        def management = new NitriteIndexManagement(databaseOf(collectionOf(
                { -> present },
                { -> present = false },
                { -> rebuilds++; present = true })))

        when: "the field was never indexed"
        def scope = management.dropUniqueIndex("bodies", "id")
        scope.close()

        then: "the configured absence of the index is preserved"
        rebuilds == 0
        !present

        when: "an indexed field is scoped and the scope is closed twice"
        present = true
        def indexedScope = management.dropUniqueIndex("bodies", "id")
        indexedScope.close()
        indexedScope.close()

        then: "the rebuild runs once"
        rebuilds == 1
        present
    }

    void "a drop that fails after removing the index restores it and reports the original failure"() {
        given:
        boolean present = true
        int rebuilds = 0
        def management = new NitriteIndexManagement(databaseOf(collectionOf(
                { -> present },
                { -> present = false; throw new IllegalStateException("drop failed") },
                { -> rebuilds++; present = true })))

        when:
        management.dropUniqueIndex("bodies", "id")

        then:
        def failure = thrown(IllegalStateException)
        failure.message == "drop failed"
        failure.suppressed.length == 0
        rebuilds == 1
        present
    }

    void "a repair that itself fails is suppressed on the original failure"() {
        given:
        boolean present = true
        def management = new NitriteIndexManagement(databaseOf(collectionOf(
                { -> present },
                { -> present = false; throw new IllegalStateException("drop failed") },
                { -> throw new IllegalStateException("rebuild failed") })))

        when:
        management.dropUniqueIndex("bodies", "id")

        then:
        def failure = thrown(IllegalStateException)
        failure.message == "drop failed"
        failure.suppressed*.message == ["rebuild failed"]
    }

    void "a drop that fails without removing the index is not repaired"() {
        given:
        int rebuilds = 0
        def management = new NitriteIndexManagement(databaseOf(collectionOf(
                { -> true },
                { -> throw new IllegalStateException("drop failed") },
                { -> rebuilds++ })))

        when:
        management.dropUniqueIndex("bodies", "id")

        then:
        def failure = thrown(IllegalStateException)
        failure.message == "drop failed"
        failure.suppressed.length == 0
        rebuilds == 0
    }

    private static NitriteCollection collectionOf(Closure<Boolean> hasIndex, Closure dropIndex, Closure createIndex) {
        [
                hasIndex   : { String... fields -> hasIndex.call() },
                dropIndex  : { String... fields -> dropIndex.call() },
                createIndex: { IndexOptions options, String... fields -> createIndex.call() }
        ] as NitriteCollection
    }

    private static Nitrite databaseOf(NitriteCollection collection) {
        [getCollection: { String name -> collection }] as Nitrite
    }
}

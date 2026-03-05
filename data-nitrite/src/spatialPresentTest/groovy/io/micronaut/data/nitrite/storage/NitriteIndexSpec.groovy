package io.micronaut.data.nitrite.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.IndexedBook
import io.micronaut.data.nitrite.repository.IndexedBookRepository
import org.dizitart.no2.Nitrite
import org.dizitart.no2.index.IndexType
import spock.lang.Specification

class NitriteIndexSpec extends Specification {

    void "test automated index creation"() {
        given:
        def ctx = ApplicationContext.run([
            "nitrite.storage-mode": "IN_MEMORY"
        ])
        def repository = ctx.getBean(IndexedBookRepository)
        def db = ctx.getBean(Nitrite)

        when: "We perform a repository operation"
        repository.save(new IndexedBook("The Stand", 1000))
        
        then: "Indexes should be present in the collection"
        def collection = db.getCollection("IndexedBook")
        def indices = collection.listIndices()

        indices.any { it.fields.fieldNames.contains("title") && it.fields.fieldNames.size() == 1 && it.indexType == IndexType.NON_UNIQUE }
        indices.any { it.fields.fieldNames.containsAll(["title", "pages"]) && it.fields.fieldNames.size() == 2 }
        indices.any { it.fields.fieldNames.contains("description") && it.indexType == IndexType.FULL_TEXT }
        indices.any { it.fields.fieldNames.contains("location") && it.indexType.toString() == "Spatial" }
        // UUID field with @Index should be indexed
        indices.any { it.fields.fieldNames.contains("indexedUuid") && it.indexType == IndexType.NON_UNIQUE }
        // UUID field without @Index should NOT be indexed
        !indices.any { it.fields.fieldNames.contains("uuid") }

        cleanup:
        ctx.close()
    }
}

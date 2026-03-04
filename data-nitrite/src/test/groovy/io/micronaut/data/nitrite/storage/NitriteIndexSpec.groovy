package io.micronaut.data.nitrite.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.IndexedBook
import io.micronaut.data.nitrite.repository.IndexedBookRepository
import org.dizitart.no2.Nitrite
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
        
        // fields property in IndexDescriptor is a Fields object which has getFieldNames()
        indices.any { it.fields.fieldNames.contains("title") && it.fields.fieldNames.size() == 1 }
        indices.any { it.fields.fieldNames.containsAll(["title", "pages"]) && it.fields.fieldNames.size() == 2 }
        indices.any { it.fields.fieldNames.contains("id") && it.fields.fieldNames.size() == 1 }

        cleanup:
        ctx.close()
    }
}

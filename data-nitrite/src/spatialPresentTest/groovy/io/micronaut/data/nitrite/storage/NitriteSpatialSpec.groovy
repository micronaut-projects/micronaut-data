package io.micronaut.data.nitrite.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.IndexedBook
import io.micronaut.data.nitrite.repository.IndexedBookRepository
import org.dizitart.no2.Nitrite
import spock.lang.Specification

class NitriteSpatialSpec extends Specification {

    void "test spatial index with null geometry"() {
        given:
        def ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
        ])
        def repository = ctx.getBean(IndexedBookRepository)

        when: "Save book with null location"
        def book = new IndexedBook("No Location", 100, "No geometry", null)
        repository.save(book)

        then: "Should handle null geometry gracefully"
        repository.findAll().size() == 1
        repository.findAll()[0].location == null

        and: "Spatial index still exists"
        def db = ctx.getBean(Nitrite)
        def collection = db.getCollection("IndexedBook")
        def indices = collection.listIndices()
        def spatialIndices = indices.findAll { it.indexType.toString() == "Spatial" }
        spatialIndices.size() == 1

        cleanup:
        ctx.close()
    }
}

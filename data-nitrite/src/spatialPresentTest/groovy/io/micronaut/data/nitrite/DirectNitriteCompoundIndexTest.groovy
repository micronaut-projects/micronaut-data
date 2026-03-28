package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.DocumentCursor
import org.dizitart.no2.collection.FindPlan
import org.dizitart.no2.filters.Filter
import org.dizitart.no2.filters.FluentFilter
import org.dizitart.no2.index.IndexType
import org.dizitart.no2.repository.ObjectRepository
import spock.lang.Specification

@MicronautTest
class DirectNitriteCompoundIndexTest extends Specification {

    @Inject
    ApplicationContext applicationContext

    def "test micronaut-data compound index is selected by FindOptimizer"() {
        given: "trigger index creation via a non-transactional findAll, then insert data directly"
        def repository = applicationContext.getBean(io.micronaut.data.nitrite.repository.IndexedBookRepository)
        def db = applicationContext.getBean(Nitrite)

        // Call findAll to trigger ensureIndexes outside a transaction (creates indexes on main collection)
        repository.findAll()

        // Insert data directly into the main collection (no transaction) so it's immediately indexed
        def collection = db.getCollection("IndexedBook")
        collection.insert(
            Document.createDocument("title", "The Stand").put("pages", 1000).put("id", "book-1"),
            Document.createDocument("title", "The Shining").put("pages", 447).put("id", "book-2"),
            Document.createDocument("title", "The Stand").put("pages", 800).put("id", "book-3")
        )

        when: "query with the same AND filter that micronaut-data generates for findByTitleAndPages"
        def cursor = collection.find(
            Filter.and(
                FluentFilter.where("title").eq("The Stand"),
                FluentFilter.where("pages").eq(1000)
            )
        )
        def findPlan = cursor.getFindPlan()

        then: "compound index [title, pages] is selected (not collection scan)"
        findPlan.indexScanFilter != null
        findPlan.collectionScanFilter == null
        findPlan.indexDescriptor != null
        findPlan.indexDescriptor.fields.fieldNames.size() == 2  // compound (2-field) index was chosen
        cursor.toList().size() == 1

        cleanup:
        collection.drop()
    }

    def "test direct nitrite compound index usage"() {
        given:
        def db = applicationContext.getBean(Nitrite)
        def collection = db.getCollection("DirectTestBook")
        
        // Create compound index directly using Nitrite API
        collection.createIndex(org.dizitart.no2.index.IndexOptions.indexOptions(IndexType.NON_UNIQUE), "title", "pages")
        
        // Insert test data using Nitrite API
        def doc1 = Document.createDocument("title", "The Stand").put("pages", 1000).put("author", "Stephen King")
        def doc2 = Document.createDocument("title", "The Shining").put("pages", 447).put("author", "Stephen King")
        def doc3 = Document.createDocument("title", "It").put("pages", 1138).put("author", "Stephen King")
        
        collection.insert(doc1, doc2, doc3)

        expect:
        collection.find().size() == 3

        when: "Use direct Nitrite API with AND filter to trigger compound index"
        def cursor = collection.find(
            Filter.and(
                FluentFilter.where("title").eq("The Stand"),
                FluentFilter.where("pages").eq(1000)
            )
        )
        
        then: "Query should use compound index"
        def findPlan = cursor.getFindPlan()
        findPlan.indexScanFilter != null  // Should use index scan
        findPlan.collectionScanFilter == null  // Should NOT use collection scan
        
        def results = cursor.toList()
        results.size() == 1
        results[0].get("title") == "The Stand"
        results[0].get("pages") == 1000

        when: "Another compound query"
        def cursor2 = collection.find(
            Filter.and(
                FluentFilter.where("author").eq("Stephen King"),
                FluentFilter.where("pages").eq(447)
            )
        )
        
        then: "This should also work"
        def results2 = cursor2.toList()
        results2.size() == 1
        results2[0].get("title") == "The Shining"
        results2[0].get("pages") == 447

        cleanup:
        collection.drop()
    }
}
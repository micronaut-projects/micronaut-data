package io.micronaut.data.document.mongodb.search

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import groovy.transform.Memoized
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.search.SearchCapability
import io.micronaut.data.search.tck.AbstractTextSearchSpec
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.data.mongodb.operations.MongoCollectionNameProvider
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider
import io.micronaut.data.search.tck.repositories.BookSearchRepository
import org.bson.Document

class MongoTextSearchSpec extends AbstractTextSearchSpec implements MongoTestPropertyProvider {

    @Memoized
    @Override
    BookSearchRepository getBookSearchRepository() {
        return context.getBean(MongoBookSearchRepository)
    }

    @Override
    String noStemmingLanguage() {
        // the text index's language_override reads the @Language property
        return "none"
    }

    @Override
    boolean supportsCursoredPaging() {
        // MongoDB repositories page by offset only
        return false
    }

    @Override
    Set<SearchCapability> capabilities() {
        // $text searches the whole text index and has no prefix matching
        return EnumSet.of(SearchCapability.PHRASE, SearchCapability.LANGUAGE_OVERRIDE)
    }

    void "an unsorted pageable keeps the relevance order, best match first"() {
        given:
        // saved weakest match first, which MongoDB returns out of relevance order when nothing sorts
        bookSearchRepository.saveAll([
                new Book("Gardening Notes", "Soil water plants seeds compost and a cup of tea", "Green", 90),
                new Book("Tea Leaves", "Tea", "Leaf", 40),
                new Book("Herbal Tea", "Mint and chamomile tea blends", "Herb", 60),
        ])

        when:
        def all = bookSearchRepository.searchBySearch("tea", Pageable.unpaged())
        def first = bookSearchRepository.search("tea", Pageable.from(0, 2))
        def second = bookSearchRepository.search("tea", Pageable.from(1, 2))
        def scores = all.results()*.score()*.value()

        then:
        all.results()*.entity()*.title == ["Tea Leaves", "Herbal Tea", "Gardening Notes"]
        scores == scores.sort(false).reverse()
        first.results()*.entity()*.title == ["Tea Leaves", "Herbal Tea"]
        second.results()*.entity()*.title == ["Gardening Notes"]
    }

    void "SearchAll matches every term literally, without stemming"() {
        expect:
        bookSearchRepository.searchByTitleSearchAll("programming language").results()*.entity()*.title == ["The Rust Programming Language"]
        bookSearchRepository.searchByTitleSearchAll("programming languages").results().isEmpty()
        bookSearchRepository.search("languages").results()*.entity()*.title == ["The Rust Programming Language"]
    }

    @Override
    String nativeProgrammingWithoutKotlinQuery() {
        return "programming -kotlin"
    }

    void "the index operations take a text index under another name for the entity's, like the startup creator"() {
        given:
        def operations = searchIndexOperations()
        def collection = context.getBean(MongoClient)
                .getDatabase(context.getBean(MongoDatabaseNameProvider).provide(Book))
                .getCollection(context.getBean(MongoCollectionNameProvider).provide(context.getBean(RuntimeEntityRegistry).getEntity(Book)))

        when: "the text index is replaced by one under another name, as a migration may leave it"
        operations.drop(Book)
        collection.createIndex(Indexes.compoundIndex(Indexes.text("title"), Indexes.text("description")), new IndexOptions().name("legacy_text"))

        then:
        operations.exists(Book)

        when:
        operations.create(Book)

        then: "the existing text index is kept rather than adding a second one, which MongoDB rejects"
        textIndexNames(collection) == ["legacy_text"]

        when:
        operations.drop(Book)

        then:
        textIndexNames(collection).isEmpty()
        !operations.exists(Book)

        cleanup:
        operations.drop(Book)
        operations.create(Book)
    }

    private static List<String> textIndexNames(MongoCollection<Document> collection) {
        return collection.listIndexes().into([]).findAll { (it.get("key") as Document).containsKey("_fts") }*.getString("name")
    }
}

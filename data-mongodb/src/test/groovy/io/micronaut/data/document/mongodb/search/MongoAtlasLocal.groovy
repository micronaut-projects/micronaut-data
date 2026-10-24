package io.micronaut.data.document.mongodb.search

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import io.micronaut.test.support.TestPropertyProvider
import org.bson.Document
import org.bson.UuidRepresentation
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer

import java.time.Duration

/**
 * Starts one MongoDB Atlas local deployment for every spec of the JVM and waits for its
 * search index to catch up with the collection, as MongoDB Search indexes asynchronously.
 */
trait MongoAtlasLocal implements TestPropertyProvider {

    static final String IMAGE = "mongodb/mongodb-atlas-local:7.0"
    static final String DATABASE = "atlas_search_test"
    private static MongoDBAtlasLocalContainer container

    static synchronized MongoDBAtlasLocalContainer atlas() {
        if (container == null) {
            container = new MongoDBAtlasLocalContainer(IMAGE)
            container.start()
        }
        return container
    }

    String getDriverType() {
        return "sync"
    }

    @Override
    Map<String, String> getProperties() {
        return [
                "mongodb.uri"                               : atlas().getDatabaseConnectionString(DATABASE),
                "mongodb.uuid-representation"               : UuidRepresentation.STANDARD.name(),
                "mongodb.package-names"                     : "io.micronaut.data.document.mongodb.search",
                "micronaut.data.mongodb.driver-type"        : getDriverType(),
                "micronaut.data.mongodb.create-collections" : "true"
        ]
    }

    /**
     * Polls until the search index returns exactly the ids the collection holds.
     */
    void awaitSearchIndex(String collection = "atlas_book", Duration timeout = Duration.ofSeconds(90)) {
        awaitIndex(collection, new Document('$search', new Document("index", "default")
                .append("exists", new Document("path", "title"))), timeout)
    }

    /**
     * Polls until the vector search index returns exactly the ids the collection holds.
     */
    void awaitVectorIndex(String collection, String path, int dimensions, Duration timeout = Duration.ofSeconds(90)) {
        awaitIndex(collection, new Document('$vectorSearch', new Document("index", "vector")
                .append("path", path)
                .append("queryVector", (1..dimensions).collect { 0.1d })
                .append("numCandidates", 100)
                .append("limit", 100)), timeout)
    }

    private void awaitIndex(String collection, Document searchStage, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos()
        try (MongoClient client = MongoClients.create(atlas().getDatabaseConnectionString(DATABASE))) {
            def coll = client.getDatabase(DATABASE).getCollection(collection)
            def pipeline = [searchStage, new Document('$project', new Document("_id", 1))]
            while (true) {
                Set stored = coll.find().projection(new Document("_id", 1)).collect { it.get("_id") } as Set
                Set indexed
                try {
                    indexed = coll.aggregate(pipeline).collect { it.get("_id") } as Set
                } catch (Exception e) {
                    indexed = null
                }
                if (indexed == stored) {
                    return
                }
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("The search index did not catch up with collection ${collection} within ${timeout}: stored ${stored.size()} indexed ${indexed?.size()}")
                }
                Thread.sleep(250)
            }
        }
    }
}

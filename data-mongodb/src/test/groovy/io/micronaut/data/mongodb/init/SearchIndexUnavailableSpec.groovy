package io.micronaut.data.mongodb.init

import com.mongodb.MongoCommandException
import com.mongodb.ServerAddress
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document
import spock.lang.Specification

class SearchIndexUnavailableSpec extends Specification {

    private static final AbstractMongoCollectionsCreator.SearchIndexDefinition DEFINITION =
            new AbstractMongoCollectionsCreator.SearchIndexDefinition("default", "lucene.english", false, ["title"])

    private static MongoCommandException commandException(int code) {
        new MongoCommandException(new BsonDocument("ok", new BsonInt32(0))
                .append("code", new BsonInt32(code))
                .append("errmsg", new BsonString("failed")), new ServerAddress())
    }

    void "a deployment without MongoDB Search only logs a warning"() {
        given:
        def creator = new AbstractMongoCollectionsCreator<Object>()
        def operations = Mock(AbstractMongoCollectionsCreator.DatabaseOperations)

        when:
        creator.createSearchIndex(operations, "db", "book", DEFINITION)

        then:
        1 * operations.listSearchIndexNames("db", "book") >> { throw commandException(code) }
        0 * operations.createSearchIndex(*_)
        noExceptionThrown()

        where: "40324 is the unrecognized \$listSearchIndexes stage of a server before 6.0.7"
        code << [59, 115, 31082, 6047401, 40324]
    }

    void "an invalid search index definition fails"() {
        given:
        def creator = new AbstractMongoCollectionsCreator<Object>()
        def operations = Mock(AbstractMongoCollectionsCreator.DatabaseOperations)

        when:
        creator.createSearchIndex(operations, "db", "book", DEFINITION)

        then:
        1 * operations.listSearchIndexNames("db", "book") >> Set.of()
        1 * operations.createSearchIndex("db", "book", DEFINITION) >> { throw commandException(8) }
        def e = thrown(MongoCommandException)
        e.errorCode == 8
    }

    private static final AbstractMongoCollectionsCreator.VectorIndexDefinition VECTOR =
            new AbstractMongoCollectionsCreator.VectorIndexDefinition("vector", "cosine", [embedding: 3])

    private static Document vectorIndex(String similarity, int numDimensions, String path = "embedding") {
        new Document("name", "vector").append("type", "vectorSearch").append("latestDefinition",
                new Document("fields", [new Document("type", "vector").append("path", path)
                        .append("numDimensions", numDimensions).append("similarity", similarity)]))
    }

    void "a deployment without MongoDB Search only logs a warning for the vector index"() {
        given:
        def creator = new AbstractMongoCollectionsCreator<Object>()
        def operations = Mock(AbstractMongoCollectionsCreator.DatabaseOperations)

        when:
        creator.createVectorIndex(operations, "db", "article", VECTOR)

        then:
        1 * operations.findSearchIndex("db", "article", "vector") >> { throw commandException(code) }
        0 * operations.createVectorIndex(*_)
        noExceptionThrown()

        where:
        code << [59, 115, 31082, 6047401, 40324]
    }

    void "an invalid vector index definition fails"() {
        given:
        def creator = new AbstractMongoCollectionsCreator<Object>()
        def operations = Mock(AbstractMongoCollectionsCreator.DatabaseOperations)

        when:
        creator.createVectorIndex(operations, "db", "article", VECTOR)

        then:
        1 * operations.findSearchIndex("db", "article", "vector") >> null
        1 * operations.createVectorIndex("db", "article", VECTOR) >> { throw commandException(8) }
        def e = thrown(MongoCommandException)
        e.errorCode == 8
    }

    void "an existing vector index is kept, whether or not it matches the entity"() {
        given:
        def creator = new AbstractMongoCollectionsCreator<Object>()
        def operations = Mock(AbstractMongoCollectionsCreator.DatabaseOperations)

        when:
        creator.createVectorIndex(operations, "db", "article", VECTOR)

        then:
        1 * operations.findSearchIndex("db", "article", "vector") >> vectorIndex(similarity, dimensions)
        0 * operations.createVectorIndex(*_)

        where:
        similarity  | dimensions
        "cosine"    | 3
        "euclidean" | 3
        "cosine"    | 4
    }

    void "a vector index matches only the same fields, dimensions and similarity"() {
        expect:
        VECTOR.matches(vectorIndex("cosine", 3))
        !VECTOR.matches(vectorIndex("euclidean", 3))
        !VECTOR.matches(vectorIndex("cosine", 4))
        !VECTOR.matches(vectorIndex("cosine", 3, "other"))
        !VECTOR.matches(new Document("name", "vector"))
    }

    void "an entity with only a vector property gets its vector index"() {
        given:
        def creator = new AbstractMongoCollectionsCreator<Object>()
        def operations = Mock(AbstractMongoCollectionsCreator.DatabaseOperations)
        def entity = new RuntimePersistentEntity(VectorOnlyItem)

        when:
        creator.createIndexes(operations, "db", "vector_only_item", entity)

        then:
        0 * operations.createSearchIndex(*_)
        0 * operations.createTextIndex(*_)
        1 * operations.findSearchIndex("db", "vector_only_item", "vector") >> null
        1 * operations.createVectorIndex("db", "vector_only_item", new AbstractMongoCollectionsCreator.VectorIndexDefinition("vector", "dotProduct", [embedding: 4]))
    }
}

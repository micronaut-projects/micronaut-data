package io.micronaut.data.mongodb.init

import com.mongodb.MongoCommandException
import com.mongodb.ServerAddress
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
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
}

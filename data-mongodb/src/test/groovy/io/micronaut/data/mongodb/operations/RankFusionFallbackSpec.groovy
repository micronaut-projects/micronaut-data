package io.micronaut.data.mongodb.operations

import com.mongodb.MongoCommandException
import com.mongodb.ServerAddress
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import spock.lang.Specification

class RankFusionFallbackSpec extends Specification {

    private static MongoCommandException commandError(int code, String message) {
        return new MongoCommandException(new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code)).append("errmsg", new BsonString(message)), new ServerAddress())
    }

    void "a server without \$rankFusion makes the hybrid search fall back to in-memory fusion"() {
        expect:
        AbstractMongoRepositoryOperations.isUnknownRankFusion(commandError(40324, "Unrecognized pipeline stage name: '\$rankFusion'"))
        AbstractMongoRepositoryOperations.isUnknownRankFusion(commandError(1, "\$rankFusion is not allowed in this context"))
        !AbstractMongoRepositoryOperations.isUnknownRankFusion(commandError(2, "bad value"))
        !AbstractMongoRepositoryOperations.isUnknownRankFusion(new IllegalStateException("closed"))
    }
}

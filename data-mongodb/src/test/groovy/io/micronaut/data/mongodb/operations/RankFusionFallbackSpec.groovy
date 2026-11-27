package io.micronaut.data.mongodb.operations

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.mongodb.MongoCommandException
import com.mongodb.ServerAddress
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.BsonString
import org.slf4j.LoggerFactory
import spock.lang.Specification

class RankFusionFallbackSpec extends Specification {

    private static MongoCommandException commandError(int code, String message) {
        return new MongoCommandException(new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code)).append("errmsg", new BsonString(message)), new ServerAddress())
    }

    void "a server without \$rankFusion makes the hybrid search fall back to in-memory fusion"() {
        expect:
        RankFusionAvailability.isUnknownRankFusion(commandError(40324, "Unrecognized pipeline stage name: '\$rankFusion'"))
        RankFusionAvailability.isUnknownRankFusion(commandError(224, "\$rankFusion is not allowed in the current feature compatibility version"))
    }

    void "other failures of a \$rankFusion aggregation are not mistaken for a missing stage"() {
        expect:
        !RankFusionAvailability.isUnknownRankFusion(commandError(1, "\$rankFusion input pipelines must begin with a scored stage"))
        !RankFusionAvailability.isUnknownRankFusion(commandError(9, "Failed to parse \$rankFusion: unknown field 'scoreDetail'"))
        !RankFusionAvailability.isUnknownRankFusion(commandError(40324, "Unrecognized pipeline stage name: '\$searchMeta2'"))
        !RankFusionAvailability.isUnknownRankFusion(commandError(2, "bad value"))
        !RankFusionAvailability.isUnknownRankFusion(new IllegalStateException("closed"))
    }

    void "the fallback is remembered and logged once"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(RankFusionAvailability)
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        def availability = new RankFusionAvailability()

        when: "another failure of the stage"
        boolean fellBack = availability.onFailure(commandError(1, "\$rankFusion input pipelines must begin with a scored stage"))

        then:
        !fellBack
        availability.isAvailable()
        appender.list.isEmpty()

        when: "the server lacks the stage, twice"
        boolean first = availability.onFailure(commandError(40324, "Unrecognized pipeline stage name: '\$rankFusion'"))
        boolean second = availability.onFailure(commandError(40324, "Unrecognized pipeline stage name: '\$rankFusion'"))

        then:
        first
        second
        !availability.isAvailable()
        appender.list.size() == 1
        appender.list[0].level == Level.WARN
        appender.list[0].formattedMessage.contains("fused in memory")

        cleanup:
        logger.detachAppender(appender)
    }
}

package io.micronaut.data.mongodb.init

import spock.lang.Specification

class SearchAnalyzerSpec extends Specification {

    void "a language maps to its MongoDB Search analyzer"() {
        expect:
        AbstractMongoCollectionsCreator.analyzerFor(language) == analyzer

        where:
        language  | analyzer
        "english" | "lucene.english"
        "German"  | "lucene.german"
        "simple"  | "lucene.simple"
        "none"    | "lucene.standard"
    }

    void "a language without an analyzer fails instead of naming one that does not exist"() {
        when:
        AbstractMongoCollectionsCreator.analyzerFor("klingon")

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("set @MongoSearchIndex(analyzer)")
    }
}

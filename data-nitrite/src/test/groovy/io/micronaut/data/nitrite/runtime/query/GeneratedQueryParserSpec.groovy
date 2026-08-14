package io.micronaut.data.nitrite.runtime.query

import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.model.Event
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteCollection
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Parsing of the SQL-shaped query string that reaches Nitrite when a query was not built as a
 * Nitrite JSON filter. Every case is asserted by running the produced filter against a real
 * collection.
 */
@MicronautTest(transactional = false)
class GeneratedQueryParserSpec extends Specification {

    @Inject
    RuntimeEntityRegistry runtimeEntityRegistry

    @Shared
    @AutoCleanup
    Nitrite database = Nitrite.builder().openOrCreate()

    private NitriteCollection collection

    def setup() {
        collection = database.getCollection("event")
        collection.remove(org.dizitart.no2.filters.Filter.ALL)
        collection.insert(
                Document.createDocument("type", "alpha").put("priority", 10).put("payload", "hello world"),
                Document.createDocument("type", "beta").put("priority", 20).put("payload", "goodbye"),
                Document.createDocument("type", "gamma").put("priority", 30).put("payload", "hello again"),
                Document.createDocument("type", "alpha AND beta").put("priority", 40).put("payload", "literal")
        )
    }

    private GeneratedQueryParser parser() {
        new GeneratedQueryParser()
    }

    private List<String> typesMatching(String query, Object... params) {
        def entity = runtimeEntityRegistry.getEntity(Event)
        def filter = parser().parseWhere(query, entity, params as Object[])
        collection.find(filter).toList().collect { it.get("type") as String }.sort()
    }

    void "an alias qualified comparison resolves to the persisted field"() {
        expect:
        typesMatching("UPDATE Event event_ SET event_.payload = :p1 WHERE event_.type = :p2", "x", "beta") == ["beta"]
    }

    void "AND and OR keep their precedence"() {
        expect:
        typesMatching("UPDATE Event SET payload = :p1 WHERE type = :p2 OR priority > :p3", "x", "alpha", 25) ==
                ["alpha", "alpha AND beta", "gamma"]
        typesMatching("UPDATE Event SET payload = :p1 WHERE (type = :p2 OR type = :p3) AND priority > :p4",
                "x", "alpha", "beta", 15) == ["beta"]
    }

    void "a quoted literal containing AND is not split into operands"() {
        expect:
        typesMatching("UPDATE Event SET payload = :p1 WHERE type = 'alpha AND beta'", "x") == ["alpha AND beta"]
    }

    void "a LIKE predicate matches the wildcard pattern"() {
        expect:
        typesMatching("UPDATE Event SET priority = :p1 WHERE payload LIKE :p2", 1, "hello%") == ["alpha", "gamma"]
    }

    void "a BETWEEN predicate matches the inclusive range"() {
        expect:
        typesMatching("UPDATE Event SET payload = :p1 WHERE priority BETWEEN :p2 AND :p3", "x", 20, 30) ==
                ["beta", "gamma"]
    }

    void "IN and IS NULL predicates are supported"() {
        expect:
        typesMatching("UPDATE Event SET payload = :p1 WHERE type IN (:p2)", "x", ["alpha", "gamma"]) ==
                ["alpha", "gamma"]
        typesMatching("UPDATE Event SET payload = :p1 WHERE processed IS NULL", "x").size() == 4
    }

    void "an unsupported predicate is rejected with an explanatory message"() {
        when:
        typesMatching("UPDATE Event SET payload = :p1 WHERE UPPER(type) = :p2", "x", "ALPHA")

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("UPPER(type)")
    }

    void "assignments resolve to persisted fields"() {
        given:
        def entity = runtimeEntityRegistry.getEntity(Event)

        when:
        def assignments = parser().parseSet(
                "UPDATE Event event_ SET event_.payload = :p1, event_.priority = :p2 WHERE event_.type = :p3",
                entity, ["updated", 99, "beta"] as Object[])

        then:
        assignments == ["payload": "updated", "priority": 99]
    }

    void "an assignment that is not a bound parameter is rejected instead of silently dropped"() {
        given:
        def entity = runtimeEntityRegistry.getEntity(Event)

        when:
        parser().parseSet("UPDATE Event SET priority = priority + 1 WHERE type = :p1",
                entity, ["alpha"] as Object[])

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("priority")
    }
}

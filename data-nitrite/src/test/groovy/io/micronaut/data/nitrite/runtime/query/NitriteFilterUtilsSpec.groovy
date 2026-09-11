package io.micronaut.data.nitrite.runtime.query

import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteCollection
import org.dizitart.no2.filters.Filter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The "match nothing" filter: that it matches nothing, and that the planner satisfies it without
 * reading the collection.
 */
class NitriteFilterUtilsSpec extends Specification {

    @Shared
    @AutoCleanup
    Nitrite database = Nitrite.builder().openOrCreate()

    private NitriteCollection collection

    def setup() {
        collection = database.getCollection("matchNone")
        collection.remove(Filter.ALL)
        collection.insert(
                Document.createDocument("id", 1L).put("name", "a"),
                Document.createDocument("id", 2L).put("name", "b"))
    }

    void "a match-nothing filter is planned as an id lookup rather than a collection scan"() {
        when:
        def cursor = collection.find(NitriteFilterUtils.matchNone())
        def rows = cursor.toList()

        then: "it matches nothing"
        rows.isEmpty()

        and: "and does so without a collection scan"
        cursor.findPlan.byIdFilter != null
        cursor.findPlan.collectionScanFilter == null
    }

    void "each call returns its own filter instance"() {
        given: "Nitrite writes the config and collection name onto a filter while planning it, so a\n" +
                "shared constant would be mutated concurrently by unrelated queries"
        expect:
        !NitriteFilterUtils.matchNone().is(NitriteFilterUtils.matchNone())
    }

    void "the impossible id cannot collide with a real one"() {
        expect: "NitriteId values come from a snowflake generator and are always positive"
        (1..200).every { org.dizitart.no2.collection.NitriteId.newId().idValue > 0L }
    }
}

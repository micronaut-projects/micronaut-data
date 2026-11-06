package io.micronaut.data.search.elasticsearch

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.search.document.runtime.BulkResponses
import spock.lang.Specification

class BulkResponsesSpec extends Specification {

    void "the ids of an all-success bulk response keep the request order"() {
        expect:
        BulkResponses.ids("Store", [[id: "a"], [id: "b"]], { it.error as String }, { it.id as String }, { 201 }) == ["a", "b"]
    }

    void "a partly rejected bulk request names every rejected item and how many were written"() {
        when:
        BulkResponses.ids("Store", [[id: "a"], [id: "b", error: "version conflict"], [id: "c"], [id: "d", error: "mapping"]],
                { it.error as String }, { it.id as String }, { it.error ? 400 : 201 })

        then:
        def e = thrown(DataAccessException)
        e.message == "Store bulk request rejected 2 of 4 documents, the other 2 were written: item 1 (id b): version conflict; item 3 (id d): mapping"
    }
}

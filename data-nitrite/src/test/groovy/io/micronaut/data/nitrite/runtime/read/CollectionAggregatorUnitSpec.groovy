package io.micronaut.data.nitrite.runtime.read

import org.dizitart.no2.collection.Document
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class CollectionAggregatorUnitSpec extends Specification {

    def "test empty collection and empty values"() {
        given:
        def aggregator = new CollectionAggregator()

        expect:
        aggregator.aggregate(null, "age", "Max") == null
        aggregator.aggregate([], "age", "Max") == null
        
        // Collection with docs but null field values
        def doc1 = Document.createDocument("name", "Alice")
        def doc2 = Document.createDocument("name", "Bob")
        aggregator.aggregate([doc1, doc2], "age", "Max") == null
    }

    def "test numeric aggregation avg and sum"() {
        given:
        def aggregator = new CollectionAggregator()
        def docs = [
                Document.createDocument("score", 10),
                Document.createDocument("score", 20),
                Document.createDocument("score", 30)
        ]

        expect:
        aggregator.aggregate(docs, "score", "Sum") == 60.0
        aggregator.aggregate(docs, "score", "Avg") == 20.0
        aggregator.aggregate(docs, "score", "Unknown") == 0 // Default branch
    }

    def "test LocalDate aggregation"() {
        given:
        def aggregator = new CollectionAggregator()
        def docs = [
                Document.createDocument("date", LocalDate.of(2023, 1, 1)),
                Document.createDocument("date", LocalDate.of(2023, 1, 15)),
                Document.createDocument("date", LocalDate.of(2023, 1, 5))
        ]

        expect:
        aggregator.aggregate(docs, "date", "Min") == LocalDate.of(2023, 1, 1)
        aggregator.aggregate(docs, "date", "Max") == LocalDate.of(2023, 1, 15)
        aggregator.aggregate(docs, "date", "Sum") == null // Unsupported for LocalDate
    }

    def "test LocalDateTime aggregation"() {
        given:
        def aggregator = new CollectionAggregator()
        def docs = [
                Document.createDocument("time", LocalDateTime.of(2023, 1, 1, 10, 0)),
                Document.createDocument("time", LocalDateTime.of(2023, 1, 1, 15, 0)),
                Document.createDocument("time", LocalDateTime.of(2023, 1, 1, 12, 0))
        ]

        expect:
        aggregator.aggregate(docs, "time", "Min") == LocalDateTime.of(2023, 1, 1, 10, 0)
        aggregator.aggregate(docs, "time", "Max") == LocalDateTime.of(2023, 1, 1, 15, 0)
        aggregator.aggregate(docs, "time", "Sum") == null // Unsupported
    }

    def "test String parsed as LocalDate aggregation"() {
        given:
        def aggregator = new CollectionAggregator()
        def docs = [
                Document.createDocument("dateStr", "2023-01-01"),
                Document.createDocument("dateStr", "2023-01-15"),
                Document.createDocument("dateStr", "2023-01-05")
        ]

        expect:
        aggregator.aggregate(docs, "dateStr", "Min") == LocalDate.of(2023, 1, 1)
        aggregator.aggregate(docs, "dateStr", "Max") == LocalDate.of(2023, 1, 15)
        aggregator.aggregate(docs, "dateStr", "Sum") == null // Unsupported
    }

    def "test fallback Comparable aggregation"() {
        given:
        def aggregator = new CollectionAggregator()
        def docs = [
                Document.createDocument("name", "Alice"),
                Document.createDocument("name", "Charlie"),
                Document.createDocument("name", "Bob")
        ]

        expect:
        aggregator.aggregate(docs, "name", "Min") == "Alice"
        aggregator.aggregate(docs, "name", "Max") == "Charlie"
        aggregator.aggregate(docs, "name", "Sum") == null
    }
}

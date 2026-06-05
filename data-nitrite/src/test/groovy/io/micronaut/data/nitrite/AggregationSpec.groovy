package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.data.nitrite.runtime.read.CollectionAggregator
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.collection.Document
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Phase 5: Aggregation execution test.
 * Exercises CollectionAggregator.executeAggregate() paths: max, min, sum, avg on numbers and dates.
 */
@MicronautTest(transactional = false)
class AggregationSpec extends Specification {

    @Inject
    EventRepository repository

    def collectionAggregator = new CollectionAggregator()

    def setup() {
        repository.deleteAll()
    }

    void "aggregation: max on numeric values"() {
        given:
        def docs = [
            Document.createDocument("amount", 100),
            Document.createDocument("amount", 50),
            Document.createDocument("amount", 200)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "amount", "Max")

        then:
        result == 200.0
    }

    void "aggregation: min on numeric values"() {
        given:
        def docs = [
            Document.createDocument("amount", 100),
            Document.createDocument("amount", 50),
            Document.createDocument("amount", 200)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "amount", "Min")

        then:
        result == 50.0
    }

    void "aggregation: sum on numeric values"() {
        given:
        def docs = [
            Document.createDocument("amount", 100),
            Document.createDocument("amount", 50),
            Document.createDocument("amount", 25)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "amount", "Sum")

        then:
        result == 175.0
    }

    void "aggregation: avg on numeric values"() {
        given:
        def docs = [
            Document.createDocument("amount", 100),
            Document.createDocument("amount", 50),
            Document.createDocument("amount", 150)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "amount", "Avg")

        then:
        Math.abs((double) result - 100.0) < 0.01
    }

    void "aggregation: max on LocalDate values"() {
        given:
        def date1 = LocalDate.of(2024, 1, 1)
        def date2 = LocalDate.of(2024, 1, 15)
        def date3 = LocalDate.of(2024, 1, 10)
        def docs = [
            Document.createDocument("dateCreated", date1),
            Document.createDocument("dateCreated", date2),
            Document.createDocument("dateCreated", date3)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "dateCreated", "Max")

        then:
        result == date2
    }

    void "aggregation: min on LocalDate values"() {
        given:
        def date1 = LocalDate.of(2024, 1, 1)
        def date2 = LocalDate.of(2024, 1, 15)
        def date3 = LocalDate.of(2024, 1, 10)
        def docs = [
            Document.createDocument("dateCreated", date1),
            Document.createDocument("dateCreated", date2),
            Document.createDocument("dateCreated", date3)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "dateCreated", "Min")

        then:
        result == date1
    }

    void "aggregation: max on LocalDateTime values"() {
        given:
        def dt1 = LocalDateTime.of(2024, 1, 1, 10, 0)
        def dt2 = LocalDateTime.of(2024, 1, 1, 15, 0)
        def dt3 = LocalDateTime.of(2024, 1, 1, 12, 0)
        def docs = [
            Document.createDocument("dateTimeCreated", dt1),
            Document.createDocument("dateTimeCreated", dt2),
            Document.createDocument("dateTimeCreated", dt3)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "dateTimeCreated", "Max")

        then:
        result == dt2
    }

    void "aggregation: empty document list returns null"() {
        given:
        def docs = []

        when:
        Object result = collectionAggregator.aggregate(docs, "amount", "Max")

        then:
        result == null
    }

    void "aggregation: null documents returns null"() {
        when:
        Object result = collectionAggregator.aggregate(null, "amount", "Max")

        then:
        result == null
    }

    void "aggregation: snake_case field name conversion"() {
        given:
        def docs = [
            Document.createDocument("custom_amount", 100),
            Document.createDocument("custom_amount", 50)
        ]

        when:
        Object result = collectionAggregator.aggregate(docs, "customAmount", "Max")

        then:
        result == 100.0
    }

    void "aggregation: extract aggregation function from method name"() {
        expect:
        collectionAggregator.extractAggFunc("findMaxAmountBy") == "Max"
        collectionAggregator.extractAggFunc("findMinAmountBy") == "Min"
        collectionAggregator.extractAggFunc("findSumAmountBy") == "Sum"
        collectionAggregator.extractAggFunc("findAvgAmountBy") == "Avg"
        collectionAggregator.extractAggFunc("notAggregation") == null
    }

    void "aggregation: extract field name from method name"() {
        expect:
        collectionAggregator.extractFieldName("findMaxAmountByStatus") == "amount"
        collectionAggregator.extractFieldName("findMinDateCreatedByStatus") == "dateCreated"
        collectionAggregator.extractFieldName("findSumTotalByStatus") == "total"
        collectionAggregator.extractFieldName("notAggregation") == null
    }

    void "aggregation: is aggregation method detection"() {
        expect:
        collectionAggregator.isAggregationMethod("findMaxAmountByStatus") == true
        collectionAggregator.isAggregationMethod("findMinDateCreatedBy") == true
        collectionAggregator.isAggregationMethod("findByStatus") == false
        collectionAggregator.isAggregationMethod("save") == false
        collectionAggregator.isAggregationMethod(null) == false
    }
}

package io.micronaut.data.nitrite.runtime.query

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.model.IndexedOrder
import io.micronaut.data.nitrite.repository.IndexedOrderRepository
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import org.dizitart.no2.filters.ComparableFilter
import spock.lang.Specification

/**
 * Nitrite's query planner selects an index by testing {@code filter instanceof ComparableFilter}
 * (FindOptimizer.planForIndexScanningFilters), so an ordering comparison expressed as a predicate
 * lambda can only ever drive a full collection scan. These tests pin the range operators to
 * Nitrite's own filter types, and check end to end that the planner does take the index that
 * NitriteCollectionRegistry created for the field.
 */
@MicronautTest(transactional = false)
class NitriteRangeFilterIndexSpec extends Specification {

    @Inject ConversionService conversionService
    @Inject ObjectMapper objectMapper
    @Inject RuntimeEntityRegistry runtimeEntityRegistry
    @Inject Nitrite nitrite
    @Inject IndexedOrderRepository repo

    private NitriteFilterBuilder builder() {
        new NitriteFilterBuilder(new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry))
    }

    private buildFilter(String op, Object value) {
        def entity = runtimeEntityRegistry.getEntity(IndexedOrder)
        builder().buildOperatorFilter(entity, "amount", op, value, new Object[0], [:])
    }

    void "range operator #op builds one of Nitrite's own comparable filters"() {
        expect:
        buildFilter(op, 100L) instanceof ComparableFilter

        where:
        op << ['$gt', '$gte', '$lt', '$lte']
    }

    void "a range filter on an indexed field is planned as an index scan"() {
        given: "rows in the collection the module created an index on"
        repo.deleteAll()
        repo.saveAll([
            new IndexedOrder("OPEN", 50L),
            new IndexedOrder("OPEN", 150L),
            new IndexedOrder("OPEN", 250L),
        ])
        def collection = nitrite.getCollection(IndexedOrder.simpleName)

        when:
        def cursor = collection.find(buildFilter('$gt', 100L))
        def plan = cursor.findPlan

        then: "the planner takes the index rather than falling back to a collection scan"
        plan.indexScanFilter != null
        plan.collectionScanFilter == null

        and: "and still returns the right rows"
        cursor.toList().size() == 2
    }

    void "a between filter on an indexed field is planned as an index scan"() {
        given:
        repo.deleteAll()
        repo.saveAll([
            new IndexedOrder("OPEN", 50L),
            new IndexedOrder("OPEN", 150L),
            new IndexedOrder("OPEN", 250L),
        ])
        def entity = runtimeEntityRegistry.getEntity(IndexedOrder)
        def filter = builder().buildOperatorFilter(entity, "amount", '$between', [100L, 200L], new Object[0], [:])
        def collection = nitrite.getCollection(IndexedOrder.simpleName)

        when:
        def cursor = collection.find(filter)

        then:
        cursor.findPlan.indexScanFilter != null
        cursor.toList().size() == 1
    }

    void "the runtime Criteria fast path shares the same native range filters"() {
        given: """NitriteCriteriaExecutor's fast path (added in 65b84f70a5) skips the JSON text
                  round trip and hands its in-memory filter map straight to buildFilterFromJson,
                  so it is a third caller of the operator registry and must index-scan too."""
        repo.deleteAll()
        repo.saveAll([
            new IndexedOrder("OPEN", 50L),
            new IndexedOrder("OPEN", 150L),
            new IndexedOrder("OPEN", 250L),
        ])
        def entity = runtimeEntityRegistry.getEntity(IndexedOrder)
        def collection = nitrite.getCollection(IndexedOrder.simpleName)

        when: "a filter map of the shape the fast path builds"
        def filter = builder().buildFilterFromJson(entity, [amount: ['$gt': 100L]], new Object[0], [:])
        def cursor = collection.find(filter)

        then:
        filter instanceof ComparableFilter
        cursor.findPlan.indexScanFilter != null
        cursor.toList().size() == 2
    }

    void "range queries through the repository still return the right rows"() {
        given:
        repo.deleteAll()
        repo.saveAll([
            new IndexedOrder("OPEN", 50L),
            new IndexedOrder("OPEN", 150L),
            new IndexedOrder("CLOSED", 250L),
        ])

        expect:
        repo.findByAmountLessThan(200L).size() == 2
        repo.findByStatusAndAmountGreaterThan("OPEN", 100L).size() == 1
        repo.findByStatusAndAmountBetween("OPEN", 100L, 200L).size() == 1
    }
}

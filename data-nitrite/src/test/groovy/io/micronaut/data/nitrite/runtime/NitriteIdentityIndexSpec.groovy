package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.City
import io.micronaut.data.nitrite.repository.CityRepository
import org.dizitart.no2.Nitrite
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.conf.NitriteConfiguration
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder
import org.dizitart.no2.collection.NitriteCollection
import org.dizitart.no2.index.IndexOptions
import org.dizitart.no2.index.IndexType

import java.util.function.Function
import spock.lang.Specification

import static org.dizitart.no2.index.IndexOptions.indexOptions

/**
 * The identity index is created for a non-Long identity on first use of the collection. Both the
 * pre-existing-index case and the case where the index cannot be built are reachable from a store
 * that already holds documents, so neither is allowed to fail the first repository call.
 */
class NitriteIdentityIndexSpec extends Specification {

    void "an identity index that cannot be built does not fail the first repository call"() {
        given: "a collection that rejects the index build"
        def context = ApplicationContext.run(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])
        def collection = [
                getName    : { -> "City" },
                hasIndex   : { String... fields -> false },
                createIndex: { IndexOptions options, String... fields ->
                    throw new IllegalStateException("index rejected")
                }
        ] as NitriteCollection
        def registry = new NitriteCollectionRegistry(
                [getCollection: { String name -> collection }] as Nitrite,
                context.getBean(NitriteTransactionHolder),
                context.getBean(NitriteConfiguration),
                { Class<?> type -> context.getBean(RuntimeEntityRegistry).getEntity(type) } as Function)

        when:
        def resolved = registry.getCollection(City)

        then: "the failure is swallowed and the collection is still usable"
        resolved.is(collection)
        !resolved.hasIndex("id")

        cleanup:
        context?.close()
    }

    void "an identity index that already exists is left alone"() {
        given:
        def context = ApplicationContext.run(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])
        def collection = context.getBean(Nitrite).getCollection("City")
        collection.createIndex(indexOptions(IndexType.UNIQUE), "id")

        when:
        def city = new City(name: "Springfield")
        def saved = context.getBean(CityRepository).save(city)

        then:
        collection.hasIndex("id")
        context.getBean(CityRepository).findById(saved.id).isPresent()

        cleanup:
        context?.close()
    }
}

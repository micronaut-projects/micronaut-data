package io.micronaut.data.nitrite

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.model.City
import io.micronaut.data.nitrite.model.State
import io.micronaut.data.nitrite.repository.CityRepository
import io.micronaut.data.nitrite.repository.StateRepository
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteOwnerCascadeTDDSpec extends Specification {

    @Inject
    CityRepository cityRepository

    @Inject
    StateRepository stateRepository

    @Inject
    Nitrite nitrite

    @Inject
    ConversionService conversionService

    @Inject
    RuntimeEntityRegistry runtimeEntityRegistry

    @Inject
    ObjectMapper serdeObjectMapper

    def setup() {
        cityRepository.deleteAll()
        stateRepository.deleteAll()
    }

    void "test cascadeProps includes owner-side ManyToOne associations"() {
        given: "Create NitriteEntityMapper manually"
            // NitriteEntityMapper is not a bean, so we create it manually for testing
            def entityMapper = new NitriteEntityMapper(conversionService, serdeObjectMapper, runtimeEntityRegistry)

        when: "Getting metadata for City entity"
            def meta = entityMapper.getOrBuildMeta(City)

        then: "cascadeProps should include the 'state' association (ManyToOne with cascade)"
            meta.cascadeProps().size() == 1
            meta.cascadeProps()[0].name == "state"
    }

    void "test cascading persist on owner side (ManyToOne) without mappedBy"() {
        given:
            def state = new State(name: "California")
            def city = new City(name: "San Francisco", state: state)

        when:
            cityRepository.save(city)

        then: "The city is saved"
            cityRepository.count() == 1
            def savedCity = cityRepository.findAll().toList()[0]
            savedCity.name == "San Francisco"

        and: "The state is cascaded and saved"
            stateRepository.count() == 1
            def savedState = stateRepository.findAll().toList()[0]
            savedState.name == "California"
            savedCity.state.id == savedState.id
    }

    void "test cascading update on owner side (ManyToOne) merges a mutated, already-saved association"() {
        given: "a city already linked to an already-saved state"
            def state = stateRepository.save(new State(name: "California"))
            def savedCity = cityRepository.save(new City(name: "Sacramento", state: state))

        when: "re-saving the city after mutating the (already-persisted) attached state"
            savedCity.state.name = "California Updated"
            cityRepository.save(savedCity)

        then: "the update cascades the mutation to the state, instead of leaving it stale"
            stateRepository.count() == 1
            def reloadedState = stateRepository.findById(state.id).get()
            reloadedState.name == "California Updated"
    }

    void "test findByStateIsNull returns cities without a state"() {
        given:
            cityRepository.save(new City(name: "Stateless City", state: null))
            def state = stateRepository.save(new State(name: "Texas"))
            cityRepository.save(new City(name: "Austin", state: state))

        when:
            def results = cityRepository.findByStateIsNull()

        then:
            results.size() == 1
            results[0].name == "Stateless City"
    }

    void "test findByStateIsNotNull returns cities with a state"() {
        given:
            cityRepository.save(new City(name: "Stateless City", state: null))
            def state = stateRepository.save(new State(name: "Nevada"))
            cityRepository.save(new City(name: "Las Vegas", state: state))

        when:
            def results = cityRepository.findByStateIsNotNull()

        then:
            results.size() == 1
            results[0].name == "Las Vegas"
    }
}

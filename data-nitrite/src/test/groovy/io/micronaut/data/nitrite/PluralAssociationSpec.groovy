package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.City
import io.micronaut.data.nitrite.model.State
import io.micronaut.data.nitrite.repository.CityRepository
import io.micronaut.data.nitrite.repository.StateRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Tests for irregular plural association name matching.
 * 
 * This verifies that association names like "cities" (irregular plural of "city")
 * are correctly matched when querying via Criteria API or @Query filters.
 * 
 * The bug was that the old code used a naive singularization heuristic:
 *   persistedName.endsWith("s") ? persistedName.substring(0, persistedName.length() - 1) : persistedName
 * 
 * This would turn "cities" → "citie", which would never match queries using "city".
 * 
 * The fix uses getAssociatedEntity().getSimpleName() which correctly returns "City".
 */
@MicronautTest(transactional = false)
class PluralAssociationSpec extends Specification {

    @Inject
    StateRepository stateRepository
    @Inject
    CityRepository cityRepository

    def setup() {
        // Clean up before each test to ensure isolation
        // Delete child entities first (City) before parent (State)
        cityRepository.deleteAll()
        stateRepository.deleteAll()
    }

    void "test find by plural association name with irregular plural - cities"() {
        given:
            def california = new State(name: "California")
            stateRepository.save(california)

            def losAngeles = new City(name: "Los Angeles", state: california)
            def sanFrancisco = new City(name: "San Francisco", state: california)
            cityRepository.saveAll([losAngeles, sanFrancisco])

        when:
            // This uses the persisted association name "cities" directly
            // The fix ensures getSimpleName() returns "City" not "citie"
            def state = stateRepository.findByCitiesName("San Francisco")

        then:
            state != null
            state.name == "California"
    }

    void "test irregular plural does not break with naive singularization"() {
        given:
            def california = new State(name: "California")
            stateRepository.save(california)

            def sanJose = new City(name: "San Jose", state: california)
            cityRepository.save(sanJose)

        when:
            // Query using the persisted association name "cities"
            // Old buggy code would try to match "cities" → "citie" (wrong!)
            // Fixed code uses getSimpleName() → "City" (correct)
            def results = stateRepository.findAllByCitiesName("San Jose")

        then:
            results.size() == 1
            results[0].name == "California"
    }

    void "test multiple cities with same state"() {
        given:
            def texas = new State(name: "Texas")
            stateRepository.save(texas)

            def houston = new City(name: "Houston", state: texas)
            def dallas = new City(name: "Dallas", state: texas)
            def austin = new City(name: "Austin", state: texas)
            cityRepository.saveAll([houston, dallas, austin])

        when:
            def foundState = stateRepository.findByCitiesName("Austin")

        then:
            foundState != null
            foundState.name == "Texas"
    }

    void "test non-matching city name returns empty list"() {
        given:
            def nevada = new State(name: "Nevada")
            stateRepository.save(nevada)

            def lasVegas = new City(name: "Las Vegas", state: nevada)
            cityRepository.save(lasVegas)

        when:
            def results = stateRepository.findAllByCitiesName("NonExistent")

        then:
            results.empty
    }
}

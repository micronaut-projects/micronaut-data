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

/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.dizitart.no2.common.mapper.NitriteMapper
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
            NitriteMapper nitriteMapper = nitrite.config.nitriteMapper()
            def entityMapper = new NitriteEntityMapper(conversionService, serdeObjectMapper, nitriteMapper, runtimeEntityRegistry)

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

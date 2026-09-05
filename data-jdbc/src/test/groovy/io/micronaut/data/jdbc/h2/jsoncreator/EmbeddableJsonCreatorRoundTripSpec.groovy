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
package io.micronaut.data.jdbc.h2.jsoncreator

import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.data.annotation.InstantiateWithDefaultConstructor
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.serde.ObjectMapper
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Round trip of issue #3752: an {@code @Embeddable} value object that Jackson builds from a single string while
 * Micronaut Data maps it to two columns. Both halves have to keep working at the same time.
 */
class EmbeddableJsonCreatorRoundTripSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    void "the @JsonCreator stays the creator of the introspection"() {
        when:
        def introspection = BeanIntrospection.getIntrospection(Country)

        then: "Micronaut Data leaves the creator Jackson claimed alone"
        introspection.constructorArguments*.name == ['value']

        and: "the processor marked the type to be instantiated with its default constructor"
        introspection.hasAnnotation(InstantiateWithDefaultConstructor)
        new RuntimePersistentEntity(introspection).constructorArguments.length == 0
    }

    void "serde still deserializes the value object from a single string"() {
        given:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)

        when:
        String json = objectMapper.writeValueAsString(new Country("US-NY"))

        then:
        json == '"US-NY"'

        when:
        Country parsed = objectMapper.readValue('"US-NY"', Country)

        then:
        parsed.countryCode == 'US'
        parsed.regionCode == 'NY'
    }

    void "data maps the value object to two columns"() {
        given:
        def repository = applicationContext.getBean(CustomerEntityRepository)

        when:
        def saved = repository.save(new CustomerEntity(null, new Country("US-NY"), "hello"))
        def loaded = repository.findById(saved.id()).orElse(null)

        then:
        loaded
        loaded.country().countryCode == 'US'
        loaded.country().regionCode == 'NY'
        loaded.data() == 'hello'

        when: "a value object without the optional part is stored"
        def withoutRegion = repository.save(new CustomerEntity(null, new Country("DE"), "world"))
        def loadedWithoutRegion = repository.findById(withoutRegion.id()).orElse(null)

        then:
        loadedWithoutRegion.country().countryCode == 'DE'
        loadedWithoutRegion.country().regionCode == null
    }

    void "an optional embedded whose columns are all null is loaded as null"() {
        given:
        def repository = applicationContext.getBean(CustomerEntityRepository)

        when:
        def saved = repository.save(new CustomerEntity(null, null, "no country"))
        def loaded = repository.findById(saved.id()).orElse(null)

        then:
        loaded.country() == null
        loaded.data() == 'no country'
    }
}

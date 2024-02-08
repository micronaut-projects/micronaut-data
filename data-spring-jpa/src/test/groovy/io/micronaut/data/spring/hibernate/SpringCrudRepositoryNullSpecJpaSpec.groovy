/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.spring.hibernate


import io.micronaut.data.spring.hibernate.spring.SpringCrudRepository
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * Tests spring JpaSpecificationExecutor with null Specification parameter.
 */
@MicronautTest(packages = "io.micronaut.data.tck.entities")
class SpringCrudRepositoryNullSpecJpaSpec extends Specification implements H2Properties {

    @Inject
    @Shared
    SpringCrudRepository crudRepository

    void "test null specification parameter"() {
        given:
        def errorMessage = 'Specification may not be null.'
        // Test calls with null specifications
        when:
        crudRepository.findOne((org.springframework.data.jpa.domain.Specification<Person>) null)
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == errorMessage
        when:
        crudRepository.findAll((org.springframework.data.jpa.domain.Specification<Person>) null)
        then:
        ex = thrown(IllegalArgumentException)
        ex.message == errorMessage
        when:
        crudRepository.count((org.springframework.data.jpa.domain.Specification<Person>) null)
        then:
        ex = thrown(IllegalArgumentException)
        ex.message == errorMessage
    }
}

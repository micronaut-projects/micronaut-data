/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanLocator
import io.micronaut.data.jdbc.config.SchemaGenerator
import io.micronaut.data.tck.entities.Person
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class H2EagerContextSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.builder(getProperties() + ['eager-test': true] + getH2DataSourceProperties("other"))
            .eagerInitSingletons(true)
            .build()
            .start()

    void "test eager start"() {
        when:
            def personRepository = context.getBean(H2PersonRepository)
        then:
            personRepository.findAll().toList().size() == 4
    }

    @Singleton
    static class SimpleService {

        private final H2PersonRepository personRepository

        SimpleService(H2PersonRepository personRepository) {
            this.personRepository = personRepository;
        }

        @PostConstruct
        void init(SchemaGenerator schemaGenerator, BeanLocator beanLocator) {
            schemaGenerator.createSchema(beanLocator)

            personRepository.save(new Person(name: 'a'))
            personRepository.save(new Person(name: 'c'))
            personRepository.save(new Person(name: 'b'))
            personRepository.save(new Person(name: 'd'))
        }
    }
}

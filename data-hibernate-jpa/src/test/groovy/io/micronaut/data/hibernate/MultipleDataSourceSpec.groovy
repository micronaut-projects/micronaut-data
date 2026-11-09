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
package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.annotation.Repository
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
@H2DBProperties
@Property(name = "datasources.other.name", value = "otherDB")
@Property(name = "datasources.other.dialect", value = "H2")
// This properties can be eliminated after TestResources bug is fixed
@Property(name = "datasources.other.driverClassName", value = "org.h2.Driver")
@Property(name = "datasources.other.url", value = "jdbc:h2:mem:otherDB;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name = "datasources.other.username", value = "")
@Property(name = "datasources.other.password", value = "")
@Property(name = 'jpa.other.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class MultipleDataSourceSpec extends Specification {

    @Inject PersonCrudRepository personRepository
    @Repository('other')
    @Inject PersonCrudRepository otherPersonRepository
    @Inject DefaultRepositoryService defaultRepositoryService
    @Inject OtherRepositoryService otherRepositoryService

    void cleanup() {
        personRepository.deleteAll()
        otherPersonRepository.deleteAll()
    }

    void "test multiple data sources"() {
        when:
        personRepository.save(new Person(name: "Fred"))
        personRepository.save(new Person(name: "Bob"))

        then:
        personRepository.count() == 2
        otherPersonRepository.count() == 0

        when:
        otherPersonRepository.save(new Person(name: "Joe"))

        then:
        otherPersonRepository.findAll().toList()[0].name == "Joe"
    }

    void "test the data source of an injection point is not taken from another one with the same name"() {
        when:
        defaultRepositoryService.repo.save(new Person(name: "Fred"))
        otherRepositoryService.repo.save(new Person(name: "Joe"))

        then:
        personRepository.findAll().collect { it.name } == ["Fred"]
        otherPersonRepository.findAll().collect { it.name } == ["Joe"]
    }

    @Singleton
    static class DefaultRepositoryService {

        final PersonCrudRepository repo

        DefaultRepositoryService(PersonCrudRepository repo) {
            this.repo = repo
        }
    }

    @Singleton
    static class OtherRepositoryService {

        final PersonCrudRepository repo

        // Same name and type as the parameter of DefaultRepositoryService: only @Repository tells them apart
        OtherRepositoryService(@Repository("other") PersonCrudRepository repo) {
            this.repo = repo
        }
    }
}

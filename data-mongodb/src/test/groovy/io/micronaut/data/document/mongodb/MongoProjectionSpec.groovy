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
package io.micronaut.data.document.mongodb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoFindQuery
import io.micronaut.data.mongodb.annotation.MongoProjection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MongoProjectionSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    PersonRepository personRepository = applicationContext.getBean(PersonRepository)

    @Shared
    @Inject
    PersonProjectionRepository personProjectionRepository = applicationContext.getBean(PersonProjectionRepository)

    @Shared
    @Inject
    PersonProjectionRepository2 personProjectionRepository2 = applicationContext.getBean(PersonProjectionRepository2)

    def cleanup() {
        personRepository.deleteAll()
    }

    def setup() {
        personRepository.saveAll([
                new XyzPerson(firstName: "John", lastName: "Doe", age: 14, education: "No"),
                new XyzPerson(firstName: "Joe", lastName: "Rabbit", age: 22, education: "Middle"),
                new XyzPerson(firstName: "Frank", lastName: "Sink", age: 33, education: "High")
        ])
    }

    void 'test projection'() {
        when:
            def all = personRepository.findAll()
        then:
            all
            all.every {
                it.id
                it.firstName
                it.lastName
                it.age
                it.education
            }
        when:
            def projected = personRepository.queryAll()
        then:
            projected
            projected.every {
                !it.id
                it.firstName
                it.lastName
                !it.age
                !it.education
            }
//        when:
//            def projected2 = personRepository.readAll()
//        then:
//            projected2
//            projected2.every {
//                !it.id
//                it.firstName
//                it.lastName
//                !it.age
//                !it.education
//            }
    }

    void 'test class projection'() {
        when:
            def projected = personProjectionRepository.findAll()
        then:
            projected.every {
                !it.id
                it.firstName
                !it.lastName
                it.age
                !it.education
            }
    }

    void 'test find query projection'() {
        when:
            def projected = personProjectionRepository2.queryAll()
        then:
            projected.every {
                !it.id
                it.firstName
                !it.lastName
                it.age
                !it.education
            }
    }

}

@MongoRepository
interface PersonRepository extends CrudRepository<XyzPerson, String> {

    @MongoProjection("{ firstName: 1, lastName: 1}")
    Iterable<XyzPerson> queryAll();

//    @MongoFindQuery(value = "{}", project = "{ firstName: 1, lastName: 1}")
//    Iterable<XyzPerson> readAll();
}

@MongoProjection("{ firstName: 1, age: 1}")
@MongoRepository
interface PersonProjectionRepository extends CrudRepository<XyzPerson, String> {
}

@MongoRepository
interface PersonProjectionRepository2 extends CrudRepository<XyzPerson, String> {

    @MongoFindQuery(value = "{}", project = "{ firstName: 1, age: 1}")
    Iterable<XyzPerson> queryAll();

}

@MappedEntity
class XyzPerson {
    @Id
    @GeneratedValue
    String id
    String firstName
    String lastName
    Integer age
    String education
}
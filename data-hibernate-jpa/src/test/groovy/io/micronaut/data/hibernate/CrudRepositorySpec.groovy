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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject

@MicronautTest(rollback = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class CrudRepositorySpec extends Specification {

    @Inject
    @Shared
    PersonCrudRepository crudRepository

    @Inject
    @Shared
    ApplicationContext context

    def setup() {
        if (crudRepository.count() == 0) {
            crudRepository.saveAll([
                    new Person(name: "Jeff"),
                    new Person(name: "James")
            ])
        }
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred")
        crudRepository.save(person)

        then:"the instance is persisted"
        person.id != null
        crudRepository.findById(person.id).isPresent()
        !crudRepository.findById(1000L).isPresent()
        crudRepository.get(person.id).name == 'Fred'
        crudRepository.existsById(person.id)
        crudRepository.count() == 3
        crudRepository.count("Fred") == 1
        crudRepository.findAll().size() == 3
        crudRepository.listPeople("Fred").size() == 1
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = crudRepository.save("Frank", 0)
        def p2 = crudRepository.save("Bob", 0)
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { crudRepository.findById(it.id).isPresent() }
        crudRepository.findAll().size() == 5
        crudRepository.count() == 5
        crudRepository.count("Fred") == 1
        crudRepository.list(Pageable.from(1)).isEmpty()
        crudRepository.list(Pageable.from(0, 1)).size() == 1

        when:"The person is updated with reactor"
        long result = Mono.from(crudRepository.updatePersonRx(crudRepository.findByName("Jeff").id)).block()

        then:
        result == 1

    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def person = crudRepository.findByName("Frank")

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        crudRepository.queryByName("Frank").name == person.name
        crudRepository.findById(person.id).isPresent()

        when:"the person is deleted"
        crudRepository.deleteById(person.id)

        then:"They are really deleted"
        !crudRepository.findById(person.id).isPresent()
        crudRepository.count() == 4
    }

    void "test delete by multiple ids"() {
        when:"A search for some people"
        def people = crudRepository.findByNameLike("J%")

        then:
        people.size() == 2

        when:"the people are deleted"
        crudRepository.deleteAll(people)

        then:"Only the correct people are deleted"
        people.every { !crudRepository.findById(it.id).isPresent() }
        crudRepository.count() == 2
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        def bob = crudRepository.findByName("Bob")

        then:"The person is present"
        bob != null

        when:"The person is deleted"
        crudRepository.delete(bob)

        then:"They are deleted"
        !crudRepository.findById(bob.id).isPresent()
        crudRepository.count() == 1
    }

    void "test update one"() {
        when:"A person is retrieved"
        def fred = crudRepository.findByName("Fred")
        def id = fred.id
        def jack = crudRepository.findByName("Jack")

        then:"The person is present"
        fred != null
        jack == null

        when:"The person name is updated"
        crudRepository.updatePerson(id, "Jack")
        fred = crudRepository.findByName("Fred")
        jack = crudRepository.findByName("Jack")

        then:"the person name is updated"
        fred == null
        jack != null
        jack.name == "Jack"

        when:"The person age is updated"
        crudRepository.updatePerson(id, 35)
        then:
        crudRepository.findById(id).get().age == 35
    }

    void "test delete all"() {
        when:"everything is deleted"
        crudRepository.deleteAll()

        then:"data is gone"
        crudRepository.count() == 0
    }

}

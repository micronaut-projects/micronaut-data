/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.hibernate.async

import io.micronaut.context.annotation.Property
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.model.Pageable
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false,packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class AsyncSpec extends Specification {

    @Inject
    AsyncPersonRepo asyncCrudRepository

    void "test async CRUD"() {
        when:"An entity is saved"
        Person p = asyncCrudRepository.save(new Person(name: "Fred", age: 18)).get()

        then:"The entity was saved"
        p != null
        p.id != null

        when:"The entity is retrieved"
        p = asyncCrudRepository.findById(p.id).get()

        then:"Tne entity is found"
        p != null
        p.name == 'Fred'
        asyncCrudRepository.existsById(p.id).get()
        asyncCrudRepository.count().get() == 1

        when:"An entity is not found"
        def notThere = asyncCrudRepository.findById(1000L)
                .exceptionally({ Throwable t ->
                    if (t.cause instanceof EmptyResultException) {
                        return null
                    }
                    throw t
                }).get()

        then:"An exception is thrown"
        notThere == null

        when:"another entity is saved and all entities are listed"
        def result = asyncCrudRepository.saveAll([new Person(name: "Bob", age: 20), new Person(name: "Chuck", age: 30)])
                .get()
        def john = asyncCrudRepository.save("John", 22).get()

        def list = asyncCrudRepository.findAll().get()
        def withLetterO = asyncCrudRepository.findAllByNameContains("o").get()
        def page = asyncCrudRepository.findAllByAgeBetween(19, 25, Pageable.from(0)).get()
        def dto = asyncCrudRepository.searchByName("Bob").get()

        then:"The results are correct"
        asyncCrudRepository.findByName("Bob").get().name == 'Bob'
        dto.age == 20
        page.totalSize == 2
        page.content.size() == 2
        page.content.find({it.name == 'Bob'})
        page.content.find({it.name == 'John'})
        john.name == 'John'
        result.size() == 2
        withLetterO.size() == 2
        list.size() == 4

        when:"an entity is updated"
        def updated = asyncCrudRepository.updateByName("Bob", 50).get()

        then:"The update is executed correctly"
        updated == 1
        asyncCrudRepository.findByName("Bob").get().age == 50

        when:"An entity is deleted"
        asyncCrudRepository.deleteById(john.id).get()
        asyncCrudRepository.delete(p).get()
        list = asyncCrudRepository.findAll().get()

        then:"The results are correct"
        list.size() == 2

        when:"All are deleted"
        asyncCrudRepository.deleteAll().get()

        then:"All are gone"
        asyncCrudRepository.count().get() == 0
    }
}

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
package io.micronaut.data.hibernate6.reactive

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate6.reactive.RxJavaPersonRepo
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.model.Pageable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.hibernate.SessionFactory
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(rollback = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class RxJava2Spec extends Specification{

    @Inject
    RxJavaPersonRepo reactiveRepo
    @Inject
    SessionFactory sessionFactory

    void "test reactive RxJava CRUD"() {
        when:"An entity is saved"
        Person p = reactiveRepo.save(new Person(name: "Fred", age: 18)).blockingGet()

        then:"The entity was saved"
        p != null
        p.id != null

        when:"The entity is retrieved"
        p = reactiveRepo.findById(p.id).blockingGet()

        then:"Tne entity is found"
        p != null
        p.name == 'Fred'
        reactiveRepo.existsById(p.id).blockingGet()
        reactiveRepo.count().blockingGet() == 1

        when:"another entity is saved and all entities are listed"
        def result = reactiveRepo.saveAll([new Person(name: "Bob", age: 20), new Person(name: "Chuck", age: 30)])
                .toList().blockingGet()
        def john = reactiveRepo.save("John", 22).blockingGet()

        def list = reactiveRepo.findAll().toList().blockingGet()
        def withLetterO = reactiveRepo.findAllByNameContains("o").toList().blockingGet()
        def page = reactiveRepo.findAllByAgeBetween(19, 25, Pageable.from(0)).blockingGet()
        def dto = reactiveRepo.searchByName("Bob").blockingGet()

        then:"The results are correct"
        reactiveRepo.findByName("Bob").blockingGet().name == 'Bob'
        dto.age == 20
        list.size() == 4
        page.totalSize == 2
        page.content.size() == 2
        page.content.find({it.name == 'Bob'})
        page.content.find({it.name == 'John'})
        john.name == 'John'
        result.size() == 2
        withLetterO.size() == 2

        when:"an entity is updated"
        def updated = reactiveRepo.updateByName("Bob", 50).blockingGet()
        sessionFactory.getCurrentSession().clear()

        then:"The update is executed correctly"
        updated == 1
        reactiveRepo.findByName("Bob").blockingGet().age == 50

        when:"An entity is deleted"
        reactiveRepo.deleteById(john.id).blockingGet() == null
        reactiveRepo.delete(p).blockingGet() == null
        list = reactiveRepo.findAll().toList().blockingGet()

        then:"The results are correct"
        list.size() == 2

        when:"All are deleted"
        reactiveRepo.deleteAll().blockingGet() == null

        then:"All are gone"
        reactiveRepo.count().blockingGet() == 0
    }
}

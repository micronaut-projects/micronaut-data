/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class PageSpec extends Specification {

    @Inject
    @Shared
    PersonRepository personRepository

    @Inject
    @Shared
    PersonCrudRepository crudRepository

    def setupSpec() {

        List<Person> people = []
        50.times { num ->
            ('A'..'Z').each {
                people << new Person(name: it * 5 + num)
            }
        }

        crudRepository.saveAll(people)
    }

    void "test sort"() {
        when:"Sorted results are returned"
        def results = personRepository.listTop10(
                Sort.unsorted().order("name", Sort.Order.Direction.DESC)
        )

        then:"The results are correct"
        results.size() == 10
        results[0].name.startsWith("Z")
    }

    void "test pageable list"() {
        when:"All the people are count"
        def count = crudRepository.count()

        then:"the count is correct"
        count == 1300

        when:"10 people are paged"
        Page<Person> page = personRepository.list(Pageable.from(0, 10))

        then:"The data is correct"
        page.content.size() == 10
        page.content.every() { it instanceof Person }
        page.content[0].name.startsWith("A")
        page.content[1].name.startsWith("B")
        page.totalSize == 1300
        page.totalPages == 130
        page.nextPageable().offset == 10
        page.nextPageable().size == 10

        when:"The next page is selected"
        page = crudRepository.findAll(page.nextPageable())

        then:"it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.content[0].name.startsWith("K")
    }

    void "test pageable sort"() {
        when:"All the people are count"
        def count = crudRepository.count()

        then:"the count is correct"
        count == 1300

        when:"10 people are paged"
        Page<Person> page = personRepository.list(
                Pageable.from(0, 10)
                        .order("name", Sort.Order.Direction.DESC)
        )

        then:"The data is correct"
        page.content.size() == 10
        page.content.every() { it instanceof Person }
        page.content[0].name.startsWith("Z")
        page.content[1].name.startsWith("Z")
        page.totalSize == 1300
        page.totalPages == 130
        page.nextPageable().offset == 10
        page.nextPageable().size == 10

        when:"The next page is selected"
        page = crudRepository.findAll(page.nextPageable())

        then:"it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.content[0].name.startsWith("Z")
    }


    void "test pageable findBy"() {
        when:"People are searched for"
        def pageable = Pageable.from(0, 10)
        Page<Person> page = personRepository.findByNameLike("A%", pageable)
        Page<Person> page2 = crudRepository.findPeople("A%", pageable)
        Slice<Person> slice = personRepository.queryByNameLike("A%", pageable)

        then:"The page is correct"
        page.offset == 0
        page.pageNumber == 0
        page.totalSize == 50
        page2.totalSize == page.totalSize
        slice.offset == 0
        slice.pageNumber == 0
        slice.size == 10
        slice.content
        page.content

        when:"The next page is retrieved"
        page = personRepository.findByNameLike("A%", page.nextPageable())

        then:"it is correct"
        page.offset == 10
        page.pageNumber == 1
        page.totalSize == 50
        page.nextPageable().offset == 20
        page.nextPageable().number == 2

    }
}

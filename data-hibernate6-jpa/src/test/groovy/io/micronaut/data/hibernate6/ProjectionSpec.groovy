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
package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate6.entities.Pet
import io.micronaut.data.tck.entities.AuthorBooksDto
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.entities.Order
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject
import java.util.stream.Collectors

@MicronautTest(rollback = false, packages = ["io.micronaut.data.tck.entities", "io.micronaut.data.hibernate6.entities"])
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class ProjectionSpec extends Specification {

    @Inject
    @Shared
    PersonCrudRepository crudRepository

    @Inject
    @Shared
    AuthorRepository authorRepository

    @Inject
    @Shared
    BookRepository bookRepository

    @Inject
    @Shared
    OrderRepo orderRepository

    @Inject
    @Shared
    PetRepository petRepository

    def setupSpec() {
        crudRepository.saveAndFlush(new Person(name: "Jeff", age: 40))
        crudRepository.saveAll([
                new Person(name: "Ivan", age: 30),
                new Person(name: "James", age: 35)
        ])

        crudRepository.flush()
        bookRepository.saveAuthorBooks([
                new AuthorBooksDto("Stephen King", Arrays.asList(
                        new BookDto("The Stand", 1000),
                        new BookDto("Pet Cemetery", 400)
                )),
                new AuthorBooksDto("James Patterson", Arrays.asList(
                        new BookDto("Along Came a Spider", 300),
                        new BookDto("Double Cross", 300)
                )),
                new AuthorBooksDto("Don Winslow", Arrays.asList(
                        new BookDto("The Power of the Dog", 600),
                        new BookDto("The Border", 700)
                ))])
        orderRepository.saveAll([
                new Order("AAA", BigDecimal.TEN, new Double("10"), 10, 10L),
                new Order("AAA", new BigDecimal("5.25"), new Double("5.25"), 5, 5L),
                new Order("BBB", BigDecimal.TEN, new Double("10"), 10, 10L)
        ])
    }

    void "test project sum on big decimal property"(){
        expect:
        orderRepository.findSumTotalAmountByCustomer("AAA") == 15.25
        orderRepository.findSumWeightByCustomer("AAA") == 15.25
        orderRepository.findSumUnitsByCustomer("AAA") == 15
        orderRepository.findSumTaxByCustomer("AAA") == 15
    }

    void "test project on single property"() {
        expect:
        bookRepository.findTop3OrderByTitle().size() == 3
        bookRepository.findTop3OrderByTitle()[0].title == 'Along Came a Spider'
        crudRepository.findAgeByName("Jeff") == 40
        crudRepository.findAgeByName("Ivan") == 30
        crudRepository.findMaxAgeByNameLike("J%") == 40
        crudRepository.findMinAgeByNameLike("J%") == 35
        crudRepository.getSumAgeByNameLike("J%") == 75
        crudRepository.getAvgAgeByNameLike("J%") == 37
        crudRepository.readAgeByNameLike("J%").sort() == [35,40]
        crudRepository.findByNameLikeOrderByAge("J%")*.age == [35,40]
        crudRepository.findByNameLikeOrderByAgeDesc("J%")*.age == [40,35]
    }

    void "test project on single ended association"() {
        expect:
        bookRepository.count() == 6
        bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .findFirst().get().title == "Pet Cemetery"
        bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                      .count() == 2
        authorRepository.findByName("Stephen King").books.size() == 2
        authorRepository.findByBooksTitle("The Stand").name == "Stephen King"
        authorRepository.findByBooksTitle("The Border").name == "Don Winslow"
        bookRepository.findByAuthorName("Stephen King").size() == 2
    }

    void "test projection on enum type"() {
        given:
        petRepository.saveAll([
                new Pet(name: "A", type: Pet.PetType.DOG),
                new Pet(name: "B", type: Pet.PetType.CAT),
                new Pet(name: "C", type: Pet.PetType.CAT)
        ])

        when:
        def types = petRepository.listDistinctType()

        then:
        types.size() == 2
        types.contains(Pet.PetType.DOG)
        types.contains(Pet.PetType.CAT)

        when:"a native query is used"
        List names = petRepository.findPetNamesNative().collect(Collectors.toList())

        then:
        names.size() == 3
        names.containsAll(["A", "B", "C"])

        when:"a native query is used"
        types = petRepository.findPetTypesNative().collect(Collectors.toList())

        then:
        types.size() == 2
        types.contains(Pet.PetType.DOG)
        types.contains(Pet.PetType.CAT)
    }
}

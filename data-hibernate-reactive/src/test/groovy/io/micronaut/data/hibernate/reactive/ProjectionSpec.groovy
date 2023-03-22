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
package io.micronaut.data.hibernate.reactive


import io.micronaut.data.hibernate.reactive.entities.Pet
import io.micronaut.data.tck.entities.AuthorBooksDto
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.entities.Order
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@MicronautTest(transactional = false, packages = ["io.micronaut.data.tck.entities", "io.micronaut.data.hibernate.entities"])
@Stepwise
class ProjectionSpec extends Specification implements PostgresHibernateReactiveProperties {

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
        crudRepository.save(new Person(name: "Jeff", age: 40)).block()
        crudRepository.saveAll([
                new Person(name: "Ivan", age: 30),
                new Person(name: "James", age: 35)
        ]).collectList().block()

        bookRepository.saveAuthorBooks(authorRepository, [
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
                ))]).block()
        orderRepository.saveAll([
                new Order("AAA", BigDecimal.TEN, new Double("10"), 10, 10L),
                new Order("AAA", new BigDecimal("5.25"), new Double("5.25"), 5, 5L),
                new Order("BBB", BigDecimal.TEN, new Double("10"), 10, 10L)
        ]).collectList().block()
    }

    void "test project sum on big decimal property"(){
        expect:
        orderRepository.findSumTotalAmountByCustomer("AAA").block() == 15.25
        orderRepository.findSumWeightByCustomer("AAA").block() == 15.25
        orderRepository.findSumUnitsByCustomer("AAA").block() == 15
        orderRepository.findSumTaxByCustomer("AAA").block() == 15
    }

    void "test project on single property"() {
        expect:
        bookRepository.findTop3OrderByTitle().collectList().block().size() == 3
        bookRepository.findTop3OrderByTitle().collectList().block()[0].title == 'Along Came a Spider'
        crudRepository.findAgeByName("Jeff").block() == 40
        crudRepository.findAgeByName("Ivan").block() == 30
        crudRepository.findMaxAgeByNameLike("J%").block() == 40
        crudRepository.findMinAgeByNameLike("J%").block() == 35
        crudRepository.getSumAgeByNameLike("J%").block() == 75
        crudRepository.getAvgAgeByNameLike("J%").block() == 37
        crudRepository.readAgeByNameLike("J%").collectList().block().sort() == [35,40]
        crudRepository.findByNameLikeOrderByAge("J%").collectList().block()*.age == [35,40]
        crudRepository.findByNameLikeOrderByAgeDesc("J%").collectList().block()*.age == [40,35]
    }

    // TODO: Re-enable when possible
    @Ignore("Temp disabled failing test")
    void "test project on single ended association"() {
        expect:
        bookRepository.count().block() == 6
        bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .blockFirst().title == "Pet Cemetery"
        bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                      .count().block() == 2
        authorRepository.searchByName("Stephen King").block().books.size() == 2
        authorRepository.findByBooksTitle("The Stand").block().name == "Stephen King"
        authorRepository.findByBooksTitle("The Border").block().name == "Don Winslow"
        bookRepository.findByAuthorName("Stephen King").collectList().block().size() == 2
    }

    void "test projection on enum type"() {
        given:
        petRepository.saveAll([
                new Pet(name: "A", type: Pet.PetType.DOG),
                new Pet(name: "B", type: Pet.PetType.CAT),
                new Pet(name: "C", type: Pet.PetType.CAT)
        ]).collectList().block()

        when:
        def types = petRepository.listDistinctType().collectList().block()

        then:
        types.size() == 2
        types.contains(Pet.PetType.DOG)
        types.contains(Pet.PetType.CAT)

        when:"a native query is used"
        List names = petRepository.findPetNamesNative().collectList().block()

        then:
        names.size() == 3
        names.containsAll(["A", "B", "C"])

        when:"a native query is used"
        types = petRepository.findPetTypesNative().collectList().block()

        then:
        types.size() == 2
        types.contains(Pet.PetType.DOG)
        types.contains(Pet.PetType.CAT)
    }
}

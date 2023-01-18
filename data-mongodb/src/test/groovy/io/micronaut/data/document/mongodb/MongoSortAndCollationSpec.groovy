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
import io.micronaut.data.mongodb.annotation.MongoCollation
import io.micronaut.data.mongodb.annotation.MongoFindQuery
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoSort
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MongoSortAndCollationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NumbersRepository numbersRepository = applicationContext.getBean(NumbersRepository)

    @Shared
    @Inject
    NumbersAlternativeRepository numbersAlternativeRepository = applicationContext.getBean(NumbersAlternativeRepository)

    @Shared
    @Inject
    NumbersSortRepository numbersSortRepository = applicationContext.getBean(NumbersSortRepository)

    @Shared
    @Inject
    NumbersSortAlternativeRepository numbersSortAlternativeRepository = applicationContext.getBean(NumbersSortAlternativeRepository)

    // Based on https://docs.mongodb.com/manual/reference/collation/#restrictions
    def static sortedAsUsNumericOrderingAsc = [-2.1, -10.0, 1.0, 2.0, 2.1, 2.2, 2.1, 2.2, 10.0, 20.0, 20.1] as float[]
    def static sortedAsUsNumericOrderingDesc = Arrays.asList(sortedAsUsNumericOrderingAsc).reverse().toArray(new float[0]) as float[]

    def cleanup() {
        numbersRepository.deleteAll()
    }

    def setupNumbers() {
        def numbers = ["1", "2", "2.1", "-2.1", "2.2", "2.10", "2.20", "-10", "10", "20", "20.1"].collect {
            new MyNumber(n: it)
        }
        numbersRepository.saveAll(numbers)
    }

    void 'test collation on method'() {
        given:
            setupNumbers()
        when:
            def basicSort = numbersRepository.findAllOrderByN().collect { it.n as float } as float[]
        then:
            basicSort == [-10.0, -2.1, 1.0, 10.0, 2.0, 2.1, 2.1, 2.2, 2.2, 20.0, 20.1] as float[]
        when:
            def sorted2 = numbersRepository.findAllSortByN().collect { it.n as float } as float[]
        then:
            sorted2 == sortedAsUsNumericOrderingAsc
        when:
            def sorted3 = numbersAlternativeRepository.findAllOrderByN().collect { it.n as float } as float[]
        then:
            sorted3 == sortedAsUsNumericOrderingAsc
        when:
            def findQuery = numbersRepository.queryAll().collect { it.n as float } as float[]
        then:
            findQuery == sortedAsUsNumericOrderingAsc
    }

    void 'test collation on class'() {
        given:
            setupNumbers()
        when:
            def sorted = numbersAlternativeRepository.findAllOrderByN().collect { it.n as float } as float[]
        then:
            sorted == sortedAsUsNumericOrderingAsc
    }

    void 'test custom sorting'() {
        given:
            setupNumbers()
        when:
            def notSorted = numbersSortRepository.find().collect { it.n as float } as float[]
        then:
            notSorted == [1.0, 2.0, 2.1, -2.1, 2.2, 2.1, 2.2, -10.0, 10.0, 20.0, 20.1] as float[]
        when:
            def sortedDesc = numbersSortRepository.findAll().collect { it.n as float } as float[]
        then:
            sortedDesc == sortedAsUsNumericOrderingDesc
        when:
            def sortedAsc = numbersSortRepository.queryAll().collect { it.n as float } as float[]
        then:
            sortedAsc == sortedAsUsNumericOrderingAsc
        when:
            def methodNameHasPrecedence = numbersSortRepository.findAllOrderByNDesc().collect { it.n as float } as float[]
        then:
            methodNameHasPrecedence == sortedAsUsNumericOrderingDesc
    }

    void 'test custom sorting on class'() {
        given:
            setupNumbers()
        when:
            def sortedByClassDef = numbersSortAlternativeRepository.findAll().collect { it.n as float } as float[]
        then:
            sortedByClassDef == sortedAsUsNumericOrderingAsc
        when:
            def customSort = numbersSortAlternativeRepository.queryAll().collect { it.n as float } as float[]
        then:
            customSort == sortedAsUsNumericOrderingDesc
        when:
            def customSortByMethodName = numbersSortAlternativeRepository.findAllOrderByNDesc().collect { it.n as float } as float[]
        then:
            customSortByMethodName == sortedAsUsNumericOrderingDesc
    }

//    void 'test collation with custom method parameter'() {
//        when:
//            def sorted4 = numbersAlternativeRepository.findAllSortByN(false).collect{ it.n as float } as float[]
//        then:
//            sorted4 == [-10.0, -2.1, 1.0, 10.0, 2.0, 2.1, 2.1, 2.2, 2.2, 20.0, 20.1] as float[]
//    }

}

@MongoRepository
interface NumbersSortRepository extends CrudRepository<MyNumber, String> {

    Iterable<MyNumber> find();

    @MongoCollation("{ locale: 'en_US', numericOrdering: true}")
    @MongoSort("{ n : -1 }")
    List<MyNumber> findAll();

    @MongoCollation("{ locale: 'en_US', numericOrdering: true}")
    @MongoSort("{ n : 1 }")
    Iterable<MyNumber> queryAll();

    @MongoCollation("{ locale: 'en_US', numericOrdering: true}")
    @MongoSort("{ n : 1 }")
    Iterable<MyNumber> findAllOrderByNDesc();

}

@MongoCollation("{ locale: 'en_US', numericOrdering: true}")
@MongoSort("{ n : 1 }")
@MongoRepository
interface NumbersSortAlternativeRepository extends CrudRepository<MyNumber, String> {

    @MongoSort("{ n : -1 }")
    Iterable<MyNumber> queryAll();

    Iterable<MyNumber> findAllOrderByNDesc();
}

@MongoRepository
interface NumbersRepository extends CrudRepository<MyNumber, String> {

    @MongoFindQuery(value = "{}", sort = "{ n : 1 }", collation = "{ locale: 'en_US', numericOrdering: true}")
    Iterable<MyNumber> queryAll();

    Iterable<MyNumber> findAllOrderByN();

    @MongoCollation("{ locale: 'en_US', numericOrdering: true}")
    Iterable<MyNumber> findAllSortByN();
}

@MongoCollation("{ locale: 'en_US', numericOrdering: true}")
@MongoRepository
interface NumbersAlternativeRepository extends CrudRepository<MyNumber, String> {

    Iterable<MyNumber> findAllOrderByN();
//
//    @MongoCollation("{ locale: 'en_US', numericOrdering: :numericOrdering}")
//    Iterable<MyNumber> findAllSortByN(boolean numericOrdering);

}

@MappedEntity
class MyNumber {
    @Id
    @GeneratedValue
    String id

    String n
}

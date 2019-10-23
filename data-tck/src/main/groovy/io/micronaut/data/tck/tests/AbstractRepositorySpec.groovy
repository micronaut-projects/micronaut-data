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
package io.micronaut.data.tck.tests

import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.model.Pageable
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.data.tck.entities.City
import io.micronaut.data.tck.entities.Company
import io.micronaut.data.tck.entities.Country
import io.micronaut.data.tck.entities.CountryRegion
import io.micronaut.data.tck.entities.CountryRegionCity
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.entities.Nose
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookDtoRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.CityRepository
import io.micronaut.data.tck.repositories.CompanyRepository
import io.micronaut.data.tck.repositories.CountryRepository
import io.micronaut.data.tck.repositories.FaceRepository
import io.micronaut.data.tck.repositories.NoseRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.repositories.RegionRepository
import spock.lang.Specification
import spock.lang.Stepwise

import java.time.LocalDate

@Stepwise
abstract class AbstractRepositorySpec extends Specification {

    abstract PersonRepository getPersonRepository()
    abstract BookRepository getBookRepository()
    abstract AuthorRepository getAuthorRepository()
    abstract CompanyRepository getCompanyRepository()
    abstract BookDtoRepository getBookDtoRepository()
    abstract CountryRepository getCountryRepository()
    abstract CityRepository getCityRepository()
    abstract RegionRepository getRegionRepository()
    abstract NoseRepository getNoseRepository()
    abstract FaceRepository getFaceRepository()


    abstract void init()

    def setupSpec() {
        init()
        setupData()
    }

    boolean isOracle() {
        return false
    }

    protected void setupData() {
        personRepository.saveAll([
                new Person(name: "Jeff"),
                new Person(name: "James")
        ])
        bookRepository.save(new Book(title: "Anonymous", totalPages: 400))
        // blank title
        bookRepository.save(new Book(title: "", totalPages: 0))
        // book without an author
        bookRepository.setupData()
    }

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred")
        personRepository.save(person)

        then:"the instance is persisted"
        person.id != null
        personRepository.findById(person.id).isPresent()
        personRepository.get(person.id).name == 'Fred'
        personRepository.existsById(person.id)
        personRepository.count() == 3
        personRepository.count("Fred") == 1
        personRepository.findAll().size() == 3
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = personRepository.save("Frank", 0)
        def p2 = personRepository.save("Bob", 0)
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { personRepository.findById(it.id).isPresent() }
        personRepository.findAll().size() == 5
        personRepository.count() == 5
        personRepository.count("Fred") == 1

        // Oracle 11g doesn't support pagination
        isOracle() || personRepository.list(Pageable.from(1)).isEmpty()
        isOracle() || personRepository.list(Pageable.from(0, 1)).size() == 1
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def person = personRepository.findByName("Frank")

        then:"the person is not null"
        person != null
        person.name == 'Frank'
        personRepository.findById(person.id).isPresent()

        when:"the person is deleted"
        personRepository.deleteById(person.id)

        then:"They are really deleted"
        !personRepository.findById(person.id).isPresent()
        personRepository.count() == 4
    }

    void "test delete by multiple ids"() {
        when:"A search for some people"
        def people = personRepository.findByNameLike("J%")

        then:
        people.size() == 2

        when:"the people are deleted"
        personRepository.deleteAll(people)

        then:"Only the correct people are deleted"
        people.every { !personRepository.findById(it.id).isPresent() }
        personRepository.count() == 2
    }

    void "test delete one"() {
        when:"A specific person is found and deleted"
        def bob = personRepository.findByName("Bob")

        then:"The person is present"
        bob != null

        when:"The person is deleted"
        personRepository.delete(bob)

        then:"They are deleted"
        !personRepository.findById(bob.id).isPresent()
        personRepository.count() == 1
    }

    void "test update one"() {
        when:"A person is retrieved"
        def fred = personRepository.findByName("Fred")

        then:"The person is present"
        fred != null

        when:"The person is updated"
        personRepository.updatePerson(fred.id, "Jack")

        then:"the person is updated"
        personRepository.findByName("Fred") == null
        personRepository.findByName("Jack") != null
    }

    void "test delete all"() {
        when:"A new person is saved"
        personRepository.save("Greg", 30)
        personRepository.save("Groot", 300)

        then:"The count is 3"
        personRepository.count() == 3

        when:"batch delete occurs"
        def deleted = personRepository.deleteByNameLike("G%")

        then:"The count is back to 1 and it entries were deleted"
        deleted == 2
        personRepository.count() == 1

        when:"everything is deleted"
        personRepository.deleteAll()

        then:"data is gone"
        personRepository.count() == 0
    }


    void "test is null or empty"() {
        expect:
        // NOTE: Oracle treats blank and null the same
        isOracle() || bookRepository.count() == 8
        isOracle() || bookRepository.findByAuthorIsNull().size() == 2
        isOracle() || bookRepository.findByAuthorIsNotNull().size() == 6
        isOracle() || bookRepository.countByTitleIsEmpty() == 1
        isOracle() || bookRepository.countByTitleIsNotEmpty() == 7
    }


    void "test string comparison methods"() {
        expect:
        authorRepository.countByNameContains("e") == 2
        authorRepository.findByNameStartsWith("S").name == "Stephen King"
        authorRepository.findByNameEndsWith("w").name == "Don Winslow"
        authorRepository.findByNameIgnoreCase("don winslow").name == "Don Winslow"
    }

    void "test project on single property"() {
        given:
        // cleanup invalid titles
        bookRepository.deleteByTitleIsEmptyOrTitleIsNull()

        personRepository.save(new Person(name: "Jeff", age: 40))
        personRepository.saveAll([
                new Person(name: "Ivan", age: 30),
                new Person(name: "James", age: 35)
        ])

        expect:
        // Oracle 11g doesn't do pagination
        isOracle() || bookRepository.findTop3OrderByTitle().size() == 3
        isOracle() || bookRepository.findTop3OrderByTitle()[0].title == 'Along Came a Spider'
        personRepository.countByAgeGreaterThan(33) == 2
        personRepository.countByAgeLessThan(33) == 1
        personRepository.findAgeByName("Jeff") == 40
        personRepository.findAgeByName("Ivan") == 30
        personRepository.findMaxAgeByNameLike("J%") == 40
        personRepository.findMinAgeByNameLike("J%") == 35
        personRepository.getSumAgeByNameLike("J%") == 75
        personRepository.getAvgAgeByNameLike("J%") == 37
        personRepository.readAgeByNameLike("J%").sort() == [35,40]
        personRepository.findByNameLikeOrderByAge("J%")*.age == [35,40]
        personRepository.findByNameLikeOrderByAgeDesc("J%")*.age == [40,35]
    }

    void "test dto projection"() {
        when:
        def results = bookDtoRepository.findByTitleLike("The%")

        then:
        results.size() == 3
        results.every { it instanceof BookDto }
        results.every { it.title.startsWith("The")}
        bookDtoRepository.findOneByTitle("The Stand").title == "The Stand"

        if (isOracle()) {
            // Oracle 11g doesn't support pagination
            return
        }

        when:"paged result check"
        def result = bookDtoRepository.searchByTitleLike("The%", Pageable.from(0))

        then:"the result is correct"
        result.totalSize == 3
        result.size == 10
        result.content.every { it instanceof BookDto }
        result.content.every { it.title.startsWith("The")}

        when:"Stream is used"
        def dto = bookDtoRepository.findStream("The Stand").findFirst().get()

        then:"The result is correct"
        dto instanceof BookDto
        dto.title == "The Stand"
    }

    void "test null argument handling" () {
        when:
        personRepository.countByAgeGreaterThan(null) == 2

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Argument [wrapper] value is null and the method parameter is not declared as nullable'

        when:
        def author = authorRepository.findByName("Stephen King")
        authorRepository.updateNickname(author.id, "SK")
        author = authorRepository.findByName("Stephen King")

        then:
        author.nickName == 'SK'

        when:
        authorRepository.updateNickname(author.id, null)
        author = authorRepository.findByName("Stephen King")

        then:
        author.nickName == null
    }

    void "test project on single ended association"() {
        expect:
        bookRepository.count() == 7
        isOracle() || bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .findFirst().get().title == "Pet Cemetery"
        isOracle() || bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .count() == 2
        authorRepository.findByBooksTitle("The Stand").name == "Stephen King"
        authorRepository.findByBooksTitle("The Border").name == "Don Winslow"
        bookRepository.findByAuthorName("Stephen King").size() == 2
    }

    void "test join on single ended association"() {
        when:
        def book = bookRepository.findByTitle("Pet Cemetery")

        then:
        book != null
        book.title == "Pet Cemetery"
        book.author != null
        book.author.id != null
        book.author.name == "Stephen King"
    }

    void "test join on many ended association"() {
        when:
        def author = authorRepository.searchByName("Stephen King")

        then:
        author != null
        author.books.size() == 2
        author.books.find { it.title == "The Stand"}
        author.books.find { it.title == "Pet Cemetery"}

        when:
        def authors = authorRepository.listAll()

        then:
        authors.every { it.books.size() == 2 }
        authors.size() == 3
    }

    void "test find by name"() {
        when:
        Person p = personRepository.getByName("Fred")

        then:
        thrown(EmptyResultException)
        personRepository.findByName("Fred") == null // declares nullable
        !personRepository.findOptionalByName("Fred").isPresent()

        when:
        personRepository.save(new Person(name: "Fred"))
        personRepository.saveAll([new Person(name: "Bob"), new Person(name: "Fredrick")])
        p = personRepository.findByName("Bob")

        then:
        p != null
        p.name == "Bob"
        personRepository.findOptionalByName("Bob").isPresent()

        when:
        def results = personRepository.findAllByName("Bob")

        then:
        results.size() == 1
        results[0].name == 'Bob'

        when:
        results = personRepository.findAllByNameLike("Fred%", Pageable.from(0, 10))

        then:
        results.size() == 2

    }


    void "test date created and last updated"() {
        when:
        def company = new Company("Apple", new URL("http://apple.com"))
        companyRepository.save(company)
        def dateCreated = company.dateCreated

        GregorianCalendar calendar = getYearMonthDay(dateCreated)
        def retrieved = companyRepository.findById(company.myId).get()

        then:
        company.myId != null
        dateCreated != null
        company.lastUpdated.toEpochMilli().toString().startsWith(company.dateCreated.getTime().toString())
        retrieved.dateCreated == calendar.time

        when:
        companyRepository.update(company.myId, "Changed")
        def company2 = companyRepository.findById(company.myId).orElse(null)

        then:
        company.dateCreated.time == dateCreated.time
        retrieved.dateCreated.time == company2.dateCreated.time
        company2.name == 'Changed'
        company2.lastUpdated.toEpochMilli() > company2.dateCreated.time
    }

    void "test query across multiple associations"() {
        given:"TODO: Figure out why this join fails on mysql"
        def specName = specificationContext.currentSpec.name
        if (specName.contains("MySql") || specName.contains("Maria")) {
            return
        }
        when:
        def spain = new Country("Spain")
        def france = new Country("France")
        countryRepository.save(spain)
        countryRepository.save(france)
        def madrid = new CountryRegion("Madrid", spain)
        def pv = new CountryRegion("Pais Vasco", spain)
        regionRepository.save(madrid)
        regionRepository.save(pv)
        def b = new CountryRegion("Bordeaux", france)
        regionRepository.save(b)
        def bdx = new City("Bordeaux", b)
        def bilbao = new City("Bilbao", pv)
        def mad = new City("Madrid", madrid)
        cityRepository.save(bdx)
        cityRepository.save(bilbao)
        cityRepository.save(mad)
        regionRepository.save(new CountryRegionCity(
                b,
                bdx
        ))
        regionRepository.save(new CountryRegionCity(
                pv,
                bilbao
        ))
        regionRepository.save(new CountryRegionCity(
                madrid,
                mad
        ))

        then:"The counts are correct"
        cityRepository.countByCountryRegionCountryName("Spain") == 2
        cityRepository.countByCountryRegionCountryName("France") == 1

        when:"A join that uses a join table is executed"
        def region = regionRepository.findByCitiesName("Bilbao")

        then:"The result is correct"
        region.name == 'Pais Vasco'

        when:"A single level join is executed"
        def results = cityRepository.findByCountryRegionCountryName("Spain")

        then:"The results include the joined table"
        results.size() == 2
        results[0].name
        results[0].id
        results[0].countryRegion
        results[0].countryRegion.name
        results[0].countryRegion.country == null

        when:"A multiple level join is executed"
        results = cityRepository.getByCountryRegionCountryName("Spain")
        results.sort { it.name }

        then:"The results include the joined table"
        results.size() == 2
        results[0].name == 'Bilbao'
        results[0].id
        results[0].countryRegion
        results[0].countryRegion.name == 'Pais Vasco'
        results[0].countryRegion.country.uuid == spain.uuid
        results[0].countryRegion.country.name == "Spain"
        results[1].name == 'Madrid'
        results[1].id
        results[1].countryRegion
        results[1].countryRegion.name == 'Madrid'
        results[1].countryRegion.country.uuid == spain.uuid
        results[1].countryRegion.country.name == "Spain"
    }

    void "test one-to-one mappedBy"() {
        when:"when a one-to-one mapped by is saved"
        def face = faceRepository.save(new Face("Bob"))
        def nose = noseRepository.save(new Nose(face: face))

        // so that we have a few records
        def anotherFace = faceRepository.save(new Face("Fred"))
        noseRepository.save(new Nose(face: anotherFace))

        then:"They are saved correctly"
        face.id
        nose.id
        nose.face.id

        when:"retrieving a face"
        face = faceRepository.findById(face.id).orElse(null)

        then:"The association is not fetched"
        face
        face.id
        face.nose == null

        when:"Querying with a join"
        face = faceRepository.queryById(face.id)

        then:"The association is fetched"
        face
        face.id
        face.name == "Bob"
        face.nose.id

        when:"querying the inverse side"
        nose = noseRepository.findById(nose.id).orElse(null)

        then:"The association is not initialized"
        nose
        nose.id
        nose.face == null
    }

    private GregorianCalendar getYearMonthDay(Date dateCreated) {
        def cal = dateCreated.toCalendar()
        def localDate = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        GregorianCalendar calendar = new GregorianCalendar(localDate.year, localDate.month.value, localDate.dayOfMonth)
        calendar
    }
}

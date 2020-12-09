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
package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.tck.entities.*
import io.micronaut.data.tck.jdbc.entities.Role
import io.micronaut.data.tck.jdbc.entities.UserRole
import io.micronaut.data.tck.repositories.*
import io.micronaut.transaction.SynchronousTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractRepositorySpec extends Specification {

    abstract PersonRepository getPersonRepository()
    abstract BookRepository getBookRepository()
    abstract AuthorRepository getAuthorRepository()
    abstract CompanyRepository getCompanyRepository()
    abstract BookDtoRepository getBookDtoRepository()
    abstract CountryRepository getCountryRepository()
    abstract CityRepository getCityRepository()
    abstract RegionRepository getRegionRepository()
    abstract CountryRegionCityRepository getCountryRegionCityRepository()
    abstract NoseRepository getNoseRepository()
    abstract FaceRepository getFaceRepository()
    abstract UserRepository getUserRepository()
    abstract UserRoleRepository getUserRoleRepository()
    abstract RoleRepository getRoleRepository()
    abstract MealRepository getMealRepository()
    abstract FoodRepository getFoodRepository()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    SynchronousTransactionManager<Connection> transactionManager = context.getBean(SynchronousTransactionManager)

    boolean isOracle() {
        return false
    }

    protected void savePersons(List<String> names) {
        personRepository.saveAll(names.collect { new Person(name: it) })
    }

    protected void setupBooks() {
        // book without an author
        bookRepository.save(new Book(title: "Anonymous", totalPages: 400))

        // blank title
        if (!isOracle()) {
            bookRepository.save(new Book(title: "", totalPages: 0))
        }

        saveSampleBooks()
    }

    protected void saveSampleBooks() {
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
    }

    protected void cleanupBooks() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
    }

    protected void cleanupData() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
        personRepository.deleteAll()
    }

    protected void cleanupMeals() {
        foodRepository.deleteAll()
        mealRepository.deleteAll()
    }

    void "test save one"() {
        given:
        savePersons(["Jeff", "James"])

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

        cleanup:
        personRepository.deleteAll()
    }

    void "test save many"() {
        given:
        savePersons(["Jeff", "James"])

        when:"many are saved"
        def p1 = personRepository.save("Frank", 0)
        def p2 = personRepository.save("Bob", 0)
        def people = [p1,p2]

        then:"all are saved"
        people.every { it.id != null }
        people.every { personRepository.findById(it.id).isPresent() }
        personRepository.findAll().size() == 4
        personRepository.count() == 4
        personRepository.count("Jeff") == 1

        // Oracle 11g doesn't support pagination
        isOracle() || personRepository.list(Pageable.from(1)).isEmpty()
        isOracle() || personRepository.list(Pageable.from(0, 1)).size() == 1

        cleanup:
        personRepository.deleteAll()
    }

    void "test delete by id"() {
        given:
        savePersons(["Jeff", "James"])

        when:"an entity is retrieved"
        def person = personRepository.findByName("Jeff")

        then:"the person is not null"
        person != null
        person.name == 'Jeff'
        personRepository.findById(person.id).isPresent()

        when:"the person is deleted"
        personRepository.deleteById(person.id)

        then:"They are really deleted"
        !personRepository.findById(person.id).isPresent()
        old(personRepository.count()) - 1 == personRepository.count()

        cleanup:
        personRepository.deleteAll()
    }

    void "test delete by multiple ids"() {
        given:
        savePersons(["Jeff", "James"])

        when:"A search for some people"
        def people = personRepository.findByNameLike("J%")

        then:
        people.size() == 2

        when:"the people are deleted"
        personRepository.deleteAll(people)

        then:"Only the correct people are deleted"
        old(personRepository.count()) - 2 == personRepository.count()
        people.every { !personRepository.findById(it.id).isPresent() }

        cleanup:
        personRepository.deleteAll()
    }

    void "test delete one"() {
        given:
        savePersons(["Bob"])

        when:"A specific person is found and deleted"
        def bob = personRepository.findByName("Bob")

        then:"The person is present"
        bob != null

        when:"The person is deleted"
        personRepository.delete(bob)

        then:"They are deleted"
        !personRepository.findById(bob.id).isPresent()
        old(personRepository.count()) - 1 == personRepository.count()
    }

    void "test update one"() {
        given:
        savePersons(["Jeff", "James"])

        when:"A person is retrieved"
        def fred = personRepository.findByName("Jeff")

        then:"The person is present"
        fred != null

        when:"The person is updated"
        personRepository.updatePerson(fred.id, "Jack")

        then:"the person is updated"
        personRepository.findByName("Jeff") == null
        personRepository.findByName("Jack") != null

        when:"an update is issued that returns a number"
        def updated = personRepository.updateByName("Jack", 20)

        then:"The result is correct"
        updated == 1
        personRepository.findByName("Jack").age == 20

        when:"A whole entity is updated"
        def jack = personRepository.findByName("Jack")
        jack.setName("Jeffrey")
        jack.setAge(30)
        personRepository.update(jack)

        then:
        personRepository.findByName("Jack") == null
        personRepository.findByName("Jeffrey").age == 30

        cleanup:
        personRepository.deleteAll()
    }

    void "test delete all"() {
        given:
        int personsWithG = personRepository.findByNameLike("G%").size()

        when:"A new person is saved"
        personRepository.save("Greg", 30)
        personRepository.save("Groot", 300)

        then:"The count is "
        old(personRepository.count()) + 2 == personRepository.count()

        when:"batch delete occurs"
        def deleted = personRepository.deleteByNameLike("G%")

        then:"The count is back to 1 and it entries were deleted"
        deleted == personsWithG + 2
        old(personRepository.count()) - (personsWithG + 2) == personRepository.count()

        when:"everything is deleted"
        personRepository.deleteAll()

        then:"data is gone"
        personRepository.count() == 0
    }

    void "test update method variations"() {
        when:
        def person = personRepository.save("Groot", 300)

        then:
        old(personRepository.count()) + 1 == personRepository.count()

        when:
        long result = personRepository.updatePersonCount(person.id, "Greg")

        then:
        personRepository.findByName("Greg")
        result == 1

        when:
        personRepository.updatePersonFuture(person.id, "Fred").get()
        personRepository.updatePersonRx(person.id, "Freddie").blockingGet()
        result = personRepository.updatePersonCustom(person.id)

        then:
        result == 1

        cleanup:
        personRepository.deleteById(person.id)
    }

    void "test is null or empty"() {
        given:
        setupBooks()

        expect:
        // NOTE: Oracle treats blank and null the same
        isOracle() || bookRepository.count() == 8
        isOracle() || bookRepository.findByAuthorIsNull().size() == 2
        isOracle() || bookRepository.findByAuthorIsNotNull().size() == 6
        isOracle() || bookRepository.countByTitleIsEmpty() == 1
        isOracle() || bookRepository.countByTitleIsNotEmpty() == 7

        cleanup:
        cleanupBooks()
    }

    void "test order by association"() {
        given:
        setupBooks()

        when:"Sorting by an assocation"
        def page = bookRepository.findAll(Pageable.from(Sort.of(
                Sort.Order.desc("author.name")
        )))

        then:
        page.content

        cleanup:
        cleanupBooks()
    }

    void "test string comparison methods"() {
        given:
        setupBooks()

        when:
        def authors = authorRepository.findByNameContains("e")
        authors.each { println it.name }

        then:
        authorRepository.countByNameContains("e") == 2
        authorRepository.findByNameStartsWith("S").name == "Stephen King"
        authorRepository.findByNameEndsWith("w").name == "Don Winslow"
        authorRepository.findByNameIgnoreCase("don winslow").name == "Don Winslow"

        cleanup:
        cleanupBooks()
    }

    void "test project on single property"() {
        given:
        setupBooks()

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

        cleanup:
        personRepository.deleteAll()
        cleanupBooks()
    }

    void "test dto projection"() {
        given:
        saveSampleBooks()

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
        def all = bookDtoRepository.queryAll(Pageable.from(0))

        then:"the result is correct"
        result.totalSize == 3
        result.size == 10
        result.content.every { it instanceof BookDto }
        result.content.every { it.title.startsWith("The")}
        all.content.every { it instanceof BookDto }
        all.content.collect { it.title }.every {  it }

        when:"Stream is used"
        def dto = bookDtoRepository.findStream("The Stand").findFirst().get()

        then:"The result is correct"
        dto instanceof BookDto
        dto.title == "The Stand"

        cleanup:
        cleanupBooks()
    }

    void "test null argument handling" () {
        given:
        savePersons(["Jeff", "James"])
        setupBooks()

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

        cleanup:
        personRepository.deleteAll()
        cleanupBooks()
    }

    void "test project on single ended association"() {
        given:
        setupBooks()

        expect:
        if (isOracle()) {
            assert bookRepository.count() == 7
        } else {
            assert bookRepository.count() == 8
        }
        isOracle() || bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .findFirst().get().title == "Pet Cemetery"
        isOracle() || bookRepository.findTop3ByAuthorNameOrderByTitle("Stephen King")
                .count() == 2
        authorRepository.findByBooksTitle("The Stand").name == "Stephen King"
        authorRepository.findByBooksTitle("The Border").name == "Don Winslow"
        bookRepository.findByAuthorName("Stephen King").size() == 2

        cleanup:
        cleanupBooks()
    }

    void "test join on single ended association"() {
        given:
        setupBooks()

        when:
        def book = bookRepository.findByTitle("Pet Cemetery")

        then:
        book != null
        book.title == "Pet Cemetery"
        book.author != null
        book.author.id != null
        book.author.name == "Stephen King"

        cleanup:
        cleanupBooks()
    }

    void "test join on many ended association"() {
        given:
        saveSampleBooks()

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
        authors.size() == 3
        authors.collect { [authorName: it.name, books: it.books.size()] }.every { it.books == 2 }


        cleanup:
        cleanupBooks()
    }

    void "test query across multiple associations"() {
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
        countryRegionCityRepository.save(new CountryRegionCity(
                b,
                bdx
        ))
        countryRegionCityRepository.save(new CountryRegionCity(
                pv,
                bilbao
        ))
        countryRegionCityRepository.save(new CountryRegionCity(
                madrid,
                mad
        ))

        then:"The counts are correct"
        cityRepository.countByCountryRegionCountryName("Spain") == 2
        cityRepository.countByCountryRegionCountryName("France") == 1

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

        when:"A join that uses a join table is executed"
        //TODO: Figure out why this join fails on mysql
        def specName = specificationContext.currentSpec.name
        if (specName.contains("MySql") || specName.contains("Maria")) {
            return
        }
        def region = regionRepository.findByCitiesName("Bilbao")

        then:"The result is correct"
        region.name == 'Pais Vasco'

        cleanup:
        cityRepository.deleteAll()
        regionRepository.deleteAll()
        countryRepository.deleteAll()
    }

    void "test find by name"() {

        when:
        personRepository.getByName("Fred")

        then:
        thrown(EmptyResultException)
        personRepository.findByName("Fred") == null // declares nullable
        !personRepository.findOptionalByName("Fred").isPresent()

        when:
        personRepository.save(new Person(name: "Fred"))
        personRepository.saveAll([new Person(name: "Bob"), new Person(name: "Fredrick")])
        Person p = personRepository.findByName("Bob")

        then:
        p != null
        p.name == "Bob"
        personRepository.findOptionalByName("Bob").isPresent()

        when:
        def results = personRepository.findAllByName("Bob")

        then:
        results.size() == 1
        results[0].name == 'Bob'

        if (isOracle()) {
            // Oracle 11g doesn't support pagination
            return
        }

        when:
        results = personRepository.findAllByNameLike("Fred%", Pageable.from(0, 10))

        then:
        results.size() == 2

        cleanup:
        personRepository.deleteAll()
    }


    void "test date created and last updated"() {
        when:
        def company = new Company("Apple", new URL("https://apple.com"))
        def google = new Company("Google", new URL("https://google.com"))
        companyRepository.save(company)
        sleep(1000)
        companyRepository.save(google)
        def dateCreated = company.dateCreated

        GregorianCalendar calendar = getYearMonthDay(dateCreated)
        def retrieved = companyRepository.findById(company.myId).get()

        then:
        company.myId != null
        dateCreated != null
        company.lastUpdated.toEpochMilli().toString().startsWith(company.dateCreated.getTime().toString())
        getYearMonthDay(retrieved.dateCreated) == calendar

        when:
        companyRepository.update(company.myId, "Changed")
        def company2 = companyRepository.findById(company.myId).orElse(null)

        then:
        company.dateCreated.time == dateCreated.time
        retrieved.dateCreated.time == company2.dateCreated.time
        company2.name == 'Changed'
        def lastUpdated = company2.lastUpdated
        lastUpdated.toEpochMilli() > company2.dateCreated.time

        when:"batch updating with the entity"
        company2.name = "Changed Again"
        sleep(500)
        companyRepository.update(company2)

        then:
        company2.lastUpdated > lastUpdated

        when:"Sorting by date created"
        def results = companyRepository.findAll(Sort.of(
                Sort.Order.desc("name")
        )).toList()

        then:"no error occurs"
        results.size() == 2
        results.first().name == 'Google'

        cleanup:
        companyRepository.deleteAll()
    }

    void "test one-to-many mappedBy"() {
        when:"a one-to-many is saved"
        def author = new Author()
        author.name = "author"

        def book1 = new Book()
        book1.title = "Book1"
        def page1 = new Page()
        page1.num = 1
        book1.getPages().add(page1)

        def book2 = new Book()
        book2.title = "Book2"
        def page21 = new Page()
        def page22 = new Page()
        page21.num = 21
        page22.num = 22
        book2.getPages().add(page21)
        book2.getPages().add(page22)

        def book3 = new Book()
        book3.title = "Book3"
        def page31 = new Page()
        def page32 = new Page()
        def page33 = new Page()
        page31.num = 31
        page32.num = 32
        page33.num = 33
        book3.getPages().add(page31)
        book3.getPages().add(page32)
        book3.getPages().add(page33)

        author.getBooks().add(book1)
        author.getBooks().add(book2)
        author.getBooks().add(book3)
        author = authorRepository.save(author)

        then: "They are saved correctly"
        println(author)
        author.id

        when:"retrieving an author"
        author = authorRepository.findById(author.id).orElse(null)

        then:"the associations are correct"
        author.getBooks().size() == 3

        def result1 = author.getBooks().find {book -> book.title == "Book1" }
        result1.pages.size() == 1
        result1.pages.find {page -> page.num = 1}

        def result2 = author.getBooks().find {book -> book.title == "Book2" }
        result2.pages.size() == 2
        result2.pages.find {page -> page.num = 21}
        result2.pages.find {page -> page.num = 22}

        def result3 = author.getBooks().find {book -> book.title == "Book3" }
        result3.pages.size() == 3
        result3.pages.find {page -> page.num = 31}
        result3.pages.find {page -> page.num = 32}
        result3.pages.find {page -> page.num = 33}

        cleanup:
        bookRepository.deleteAll()
        authorRepository.deleteAll()
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

        cleanup:
        noseRepository.deleteAll()
        faceRepository.deleteAll()
    }

    void "test a composite primary key with relations"() {
        io.micronaut.data.tck.jdbc.entities.User adminUser = userRepository.save(new io.micronaut.data.tck.jdbc.entities.User("admin@gmail.com"))
        io.micronaut.data.tck.jdbc.entities.User user = userRepository.save(new io.micronaut.data.tck.jdbc.entities.User("user@gmail.com"))
        Role adminRole = roleRepository.save(new Role("ROLE_ADMIN"))
        Role role = roleRepository.save(new Role("ROLE_USER"))

        when:
        UserRole userRole = userRoleRepository.save(adminUser, adminRole)

        then:
        userRoleRepository.count() == 1
        userRole.user.id == adminUser.id
        userRole.role.id == adminRole.id

        when:
        userRoleRepository.save(adminUser, role)
        userRoleRepository.save(user, role)

        then:
        userRoleRepository.count() == 3

        when:
        List<Role> roles = userRoleRepository.findRoleByUser(adminUser).toList()

        then:
        roles.size() == 2
        roles.stream().anyMatch {r -> r.name == "ROLE_ADMIN" }
        roles.stream().anyMatch {r -> r.name == "ROLE_USER" }

        when:
        userRoleRepository.delete(user, role)

        then:
        userRoleRepository.count() == 2
    }

    void "test finding authors by book"() {
        given:
        setupBooks()

        when:
        def book = bookRepository.findByTitle("Pet Cemetery")
        def author = bookRepository.findAuthorById(book.id)

        then:
        author.name == "Stephen King"

        cleanup:
        cleanupBooks()
    }

    void "test finding by UUID"() {
        when:
        Meal meal = mealRepository.save(new Meal(100))

        then:
        meal != null

        expect:
        mealRepository.findById(meal.mid).get().currentBloodGlucose == 100

        cleanup:
        cleanupMeals()
    }

    void "test find one for update"() {
        given:
        def meal = mealRepository.save(new Meal(10))
        def food = foodRepository.save(new Food("food", 80, 200, meal))

        when:
        def mealById = transactionManager.executeWrite { mealRepository.findByIdForUpdate(meal.mid) }
        then:
        meal.currentBloodGlucose == mealById.currentBloodGlucose

        when: "finding with associations"
        def mealWithFood = transactionManager.executeWrite { mealRepository.searchByIdForUpdate(meal.mid) }
        then: "the association is fetched"
        food.carbohydrates == mealWithFood.foods.first().carbohydrates

        cleanup:
        cleanupMeals()
    }

    void "test find many for update"() {
        given:
        def meals = mealRepository.saveAll([
                new Meal(10),
                new Meal(20),
                new Meal(30)
        ])
        foodRepository.saveAll(meals.collect { new Food("food", 10, 100, it) })

        when:
        def mealsForUpdate = transactionManager.executeWrite { forUpdateMethod.call(*args) }

        then:
        mealsForUpdate.collect { it.currentBloodGlucose }.sort() ==
                normalMethod.call(*args).collect { it.currentBloodGlucose }.sort()

        cleanup:
        cleanupMeals()

        where:
        forUpdateMethod                                               | normalMethod                                         | args
        mealRepository::findAllForUpdate                              | mealRepository::findAll                              | []
        mealRepository::findAllByCurrentBloodGlucoseLessThanForUpdate | mealRepository::findAllByCurrentBloodGlucoseLessThan | [100]
        mealRepository::findByFoodsPortionGramsGreaterThanForUpdate   | mealRepository::findByFoodsPortionGramsGreaterThan   | [10]
    }

    void "test find for update locking"() {
        given:
        def meal = mealRepository.save(new Meal(10))
        def threadCount = 2

        when:
        def latch = new CountDownLatch(threadCount)
        (1..threadCount).collect {
            Thread.start {
                transactionManager.executeWrite {
                    def mealToUpdate = mealRepository.findByIdForUpdate(meal.mid)
                    latch.countDown()
                    latch.await(5, TimeUnit.SECONDS)
                    mealToUpdate.currentBloodGlucose++
                    mealRepository.update(mealToUpdate)
                }
            }
        }.forEach { it.join() }

        then:
        mealRepository.findById(meal.mid).get().currentBloodGlucose == meal.currentBloodGlucose + threadCount

        cleanup:
        cleanupMeals()
    }

    void "test find for update locking with associations"() {
        given:
        def meal = mealRepository.save(new Meal(10))
        foodRepository.save(new Food("food", 80, 200, meal))
        def threadCount = 2

        when:
        def latch = new CountDownLatch(threadCount)
        (1..threadCount).collect {
            Thread.start {
                transactionManager.executeWrite {
                    def food = foodRepository.findByMealMidForUpdate(meal.mid)
                    def mealToUpdate = food.meal
                    latch.countDown()
                    latch.await(5, TimeUnit.SECONDS)
                    mealToUpdate.currentBloodGlucose++
                    mealRepository.update(mealToUpdate)
                }
            }
        }.forEach { it.join() }

        then:
        mealRepository.findById(meal.mid).get().currentBloodGlucose == meal.currentBloodGlucose + threadCount

        cleanup:
        cleanupMeals()
    }

    void "test IN queries"() {
        given:
            setupBooks()
        when:
            def books1 = bookRepository.listNativeBooksWithTitleInCollection(null)
        then:
            books1.size() == 0
        when:
            def books2 = bookRepository.listNativeBooksWithTitleInCollection(["The Stand", "Along Came a Spider", "FFF"])
        then:
            books2.size() == 2
        when:
            def books3 = bookRepository.listNativeBooksWithTitleInCollection([])
        then:
            books3.size() == 0
        when:
            def books4 = bookRepository.listNativeBooksWithTitleInArray(null)
        then:
            books4.size() == 0
        when:
            def books5 = bookRepository.listNativeBooksWithTitleInArray(new String[] {"The Stand", "Along Came a Spider", "FFF"})
        then:
            books5.size() == 2
        when:
            def books6 = bookRepository.listNativeBooksWithTitleInArray(new String[0])
        then:
            books6.size() == 0
        cleanup:
            cleanupBooks()
    }

    private GregorianCalendar getYearMonthDay(Date dateCreated) {
        def cal = dateCreated.toCalendar()
        def localDate = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        GregorianCalendar calendar = new GregorianCalendar(localDate.year, localDate.month.value, localDate.dayOfMonth)
        calendar
    }
}

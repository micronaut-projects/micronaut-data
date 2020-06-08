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
package io.micronaut.data.jdbc.h2

import io.micronaut.data.tck.entities.Car
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.entities.FaceDTO
import io.micronaut.data.tck.entities.Nose
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BookDtoRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.CityRepository
import io.micronaut.data.tck.repositories.CompanyRepository
import io.micronaut.data.tck.repositories.CountryRepository
import io.micronaut.data.tck.repositories.FaceRepository
import io.micronaut.data.tck.repositories.NoseRepository
import io.micronaut.data.tck.repositories.RegionRepository
import io.micronaut.data.tck.tests.AbstractRepositorySpec
import spock.lang.Shared

class H2RepositorySpec extends AbstractRepositorySpec implements H2TestPropertyProvider {

    @Shared
    H2PersonRepository pr = context.getBean(H2PersonRepository)

    @Shared
    H2BookRepository br = context.getBean(H2BookRepository)

    @Shared
    H2AuthorRepository ar = context.getBean(H2AuthorRepository)

    @Shared
    H2CompanyRepository cr = context.getBean(H2CompanyRepository)

    @Shared
    H2BookDtoRepository dto = context.getBean(H2BookDtoRepository)

    @Shared
    H2CountryRepository countryr = context.getBean(H2CountryRepository)

    @Shared
    H2CityRepository cityr = context.getBean(H2CityRepository)

    @Shared
    H2RegionRepository regr = context.getBean(H2RegionRepository)

    @Shared
    H2FaceRepository fr = context.getBean(H2FaceRepository)

    @Shared
    H2NoseRepository nr = context.getBean(H2NoseRepository)

    @Shared
    H2CarRepository carRepo = context.getBean(H2CarRepository)

    @Override
    NoseRepository getNoseRepository() {
        return nr
    }

    @Override
    FaceRepository getFaceRepository() {
        return fr
    }

    @Override
    H2PersonRepository getPersonRepository() {
        return pr
    }

    @Override
    BookRepository getBookRepository() {
        return br
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return ar
    }

    @Override
    CompanyRepository getCompanyRepository() {
        return cr
    }

    @Override
    BookDtoRepository getBookDtoRepository() {
        return dto
    }

    @Override
    CountryRepository getCountryRepository() {
        return countryr
    }

    @Override
    CityRepository getCityRepository() {
        return cityr
    }

    @Override
    RegionRepository getRegionRepository() {
        return regr
    }

    void "test total dto"() {
        given:
        savePersons(["Jeff", "James"])

        expect:
        pr.getTotal().total == 2

        cleanup:
        personRepository.deleteAll()
    }

    void "test repositories are singleton"() {
        expect:
        pr.is(context.getBean(H2PersonRepository))
    }

    void "test CRUD with custom schema and catalog"() {
        when:
        def a5 = carRepo.save(new Car(name: "A5"))

        then:
        a5.id


        when:
        a5 = carRepo.findById(a5.id).orElse(null)

        then:
        a5.id
        a5.name == 'A5'
        carRepo.getById(a5.id).parts.size() == 0

        when:"an update happens"
        carRepo.update(a5.id, "A6")
        a5 = carRepo.findById(a5.id).orElse(null)

        then:"the updated worked"
        a5.name == 'A6'

        when:"an update to null happens"
            carRepo.update(a5.id, null)
            a5 = carRepo.findById(a5.id).orElse(null)

        then:"the updated to null worked"
            a5.name == null

        when:"A deleted"
        carRepo.deleteById(a5.id)

        then:"It was deleted"
        !carRepo.findById(a5.id).isPresent()

        cleanup:
        carRepo.deleteAll()
    }

    void "test manual joining on many ended association"() {
        given:
        saveSampleBooks()

        when:
        def author = br.findByName("Stephen King")

        then:
        author != null
        author.name == "Stephen King"
        author.books.size() == 2
        author.books.find { it.title == "The Stand"}
        author.books.find { it.title == "Pet Cemetery"}

        cleanup:
        cleanupData()
    }

    void "test SQL mapping function"() {
        given:
        saveSampleBooks()

        when:"using a function that maps a single value"
        def book = ar.testReadSingleProperty("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'

        when:"using a function that maps an associated entity value"
        book = ar.testReadAssociatedEntity("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'
        book.author.id

        when:"using a function that maps a DTO"
        book = ar.testReadDTO("The Stand", 700)

        then:"The result is correct"
        book != null
        book.author.name == 'Stephen King'

        then:
        cleanupData()
    }

    void "test custom alias"() {
        given:
        saveSampleBooks()

        when:
        def book = br.queryByTitle("The Stand")

        then:
        book.title == "The Stand"
        book.author != null
        book.author.name == "Stephen King"

        cleanup:
        cleanupData()
    }

    void "test @Query with DTO"() {
        given:
        saveSampleBooks()

        when:
        def book = dto.findByTitleWithQuery("The Stand")

        then:
        book.isPresent()
        book.get().title == "The Stand"

        cleanup:
        cleanupData()
    }
}

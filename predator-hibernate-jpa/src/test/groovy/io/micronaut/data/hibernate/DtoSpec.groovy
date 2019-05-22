package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class DtoSpec extends Specification {

    @Inject
    @Shared
    BookRepository bookRepository
    @Inject
    @Shared
    BookDtoRepository bookDtoRepository

    void setupSpec() {
        bookRepository.setupData()
    }


    void "test dto projection"() {
        when:
        def results = bookDtoRepository.findByTitleLike("The%")

        then:
        results.size() == 3
        results.every { it.title.startsWith("The")}
    }

}

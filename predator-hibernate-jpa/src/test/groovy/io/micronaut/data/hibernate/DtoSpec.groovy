package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
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
        results.every { it instanceof BookDto }
        results.every { it.title.startsWith("The")}
        bookDtoRepository.findOneByTitle("The Stand").title == "The Stand"

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

}

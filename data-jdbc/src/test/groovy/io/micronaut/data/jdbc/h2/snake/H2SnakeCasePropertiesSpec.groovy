package io.micronaut.data.jdbc.h2.snake

import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class H2SnakeCasePropertiesSpec extends Specification {

    @Inject CBookRepository bookRepo
    @Inject CAuthorRepository authorRepo

    void "snake_case properties work with both method styles"() {
        given:
        def a = new CAuthor()
        a.setName("SN1")
        a = authorRepo.save(a)
        def b = new CBook()
        b.setAuthor(a)
        b.setTitle("T")
        b.setTotal_pages(321)
        b = bookRepo.save(b)
        expect:
        bookRepo.find_by_total_pages(321).get().getId() == b.getId()
        bookRepo.findByTotalPages(321).get().getId() == b.getId()
        when:
        List<CBook> r1 = bookRepo.find_by_author_name("SN1")
        List<CBook> r2 = bookRepo.findByAuthorName("SN1")
        then:
        r1*.id.contains(b.id)
        r2*.id.contains(b.id)
    }
}

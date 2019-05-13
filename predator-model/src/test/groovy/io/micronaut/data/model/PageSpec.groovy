package io.micronaut.data.model

import spock.lang.Specification

class PageSpec extends Specification {

    void "test page"() {
        given:
        def page = Page.of([1, 2, 3, 4, 5], Pageable.from(offset, max), total)

        expect:
        page.size == max
        page.offset == offset
        page.totalSize == total
        page.totalPages == pages
        page.getNumberOfElements() == 5
        page.nextPageable().size == max
        page.nextPageable().offset == offset + max

        where:
        offset | max | total | pages
        0      | 5   | 14    | 3
        10     | 20  | 140   | 7
    }
}

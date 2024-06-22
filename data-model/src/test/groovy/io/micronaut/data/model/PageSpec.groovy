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
package io.micronaut.data.model

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.core.type.Argument
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.inject.Inject

@MicronautTest
class PageSpec extends Specification {
    @Inject ObjectMapper mapper

    @Inject io.micronaut.serde.ObjectMapper serdeMapper

    @Unroll
    void "test page for page number #number and size #size"() {
        given:
        def page = Page.of([1, 2, 3, 4, 5], Pageable.from(number, size), total)

        expect:
        page.pageNumber == number
        page.size == size
        page.offset == offset
        page.totalSize == total
        page.totalPages == pages
        page.getNumberOfElements() == 5
        page.nextPageable().size == size
        page.nextPageable().offset == offset + size

        where:
        number | offset | size | total | pages
        0      | 0      | 5    | 14    | 3
        1      | 20     | 20   | 140   | 7
        2      | 40     | 20   | 140   | 7
    }

    void "test mapping a page"() {
        def page = Page.of([1, 2, 3, 4, 5], Pageable.from(0, 5), 14)

        when:
        Page newPage = page.map({ i -> i + 1 })

        then:
        newPage.content == [2,3,4,5,6]
        newPage.totalSize == 14
        newPage.size == 5
    }

    void "test serialization and deserialization of a page"() {
        def page = Page.of([new Dummy(
                propertyOne: "value one",
                propertyTwo: 1L,
                propertyThree: new BigDecimal("1.00")
        ), new Dummy(
                propertyOne: "value two",
                propertyTwo: 2L,
                propertyThree: new BigDecimal("2.00")
        ), new Dummy(
                propertyOne: "value three",
                propertyTwo: 3L,
                propertyThree: new BigDecimal("3.00")
        )], Pageable.from(0, 3), 14)

        when:
        def json = mapper.writeValueAsString(page)

        then:
        def deserializedPage = mapper.readValue(json, mapper.typeFactory.constructParametricType(Page, Dummy))
        deserializedPage.content.every { it instanceof Dummy }
        deserializedPage == page
    }

    void "test serialization and deserialization of a page - serde"() {
        def page = Page.of([new Dummy(
                propertyOne: "value one",
                propertyTwo: 1L,
                propertyThree: new BigDecimal("1.00")
        ), new Dummy(
                propertyOne: "value two",
                propertyTwo: 2L,
                propertyThree: new BigDecimal("2.00")
        ), new Dummy(
                propertyOne: "value three",
                propertyTwo: 3L,
                propertyThree: new BigDecimal("3.00")
        )], Pageable.from(0, 3), 14)

        when:
        def json = serdeMapper.writeValueAsString(page)

        then:
        def deserializedPage = serdeMapper.readValue(json, Argument.of(Page, Dummy))
        deserializedPage.content.every { it instanceof Dummy }
        deserializedPage == page
    }

    void "test serialization and deserialization of a pageable - serde"() {
        def pageable = Pageable.from(0, 3)

        when:
        def json = serdeMapper.writeValueAsString(pageable)

        then:
        json == '{"size":3,"number":0,"sort":{},"mode":"OFFSET"}'
        def deserializedPageable = serdeMapper.readValue(json, Pageable)
        deserializedPageable == pageable

        when:
        def json2 = '{"size":3,"number":0,"sort":{}}'
        def deserializedPageable2 = serdeMapper.readValue(json2, Pageable)

        then:
        deserializedPageable2 == pageable
    }

    void "test serialization and deserialization of a cursored pageable - serde"() {
        def pageable = Pageable.afterCursor(
                Pageable.Cursor.of("value1", 2),
                0, 3, Sort.UNSORTED
        )

        when:
        def json = serdeMapper.writeValueAsString(pageable)

        then:
        json == '{"size":3,"cursor":{"elements":["value1",2]},"mode":"CURSOR_NEXT","number":0,"sort":{},"requestTotal":true}'
        def deserializedPageable = serdeMapper.readValue(json, Pageable)
        deserializedPageable == pageable
        def deserializedPageable2 = serdeMapper.readValue(json, CursoredPageable)
        deserializedPageable2 == pageable
    }

    void "test sort serialization"() {
        def sort = Sort.of(Sort.Order.asc("property"))

        when:
        def json = serdeMapper.writeValueAsString(sort)

        then:
        json == '{"orderBy":[{"ignoreCase":false,"direction":"ASC","property":"property","ascending":true}]}'
        def deserializedSort = serdeMapper.readValue(json, Sort)
        deserializedSort == sort
    }

    void "test empty page map"() {
        when:"Map empty page"
        def page = Page.empty()
        def mappedPage = page.map { it }
        then:"No exception thrown, page is mapped"
        page.size == -1
        !page.hasTotalSize()
        mappedPage.size == -1
        !mappedPage.hasTotalSize()

        when:"Map empty cursored page"
        def cursoredPage = CursoredPage.empty()
        def mappedCursoredPage = cursoredPage.map { it }
        then:"No exception thrown, cursored page is mapped"
        cursoredPage.size == -1
        !cursoredPage.cursors
        !cursoredPage.hasTotalSize()
        mappedCursoredPage.size == -1
        !mappedCursoredPage.cursors
        !mappedCursoredPage.hasTotalSize()
    }

    @EqualsAndHashCode
    @ToString
    @Serdeable
    static class Dummy {
        String propertyOne
        Long propertyTwo
        BigDecimal propertyThree
    }
}

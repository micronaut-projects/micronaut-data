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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import spock.lang.Specification
import spock.lang.Unroll

class PageSpec extends Specification {

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
        def mapper = new ObjectMapper()

        when:
        def json = mapper.writeValueAsString(page)

        then:
        def deserializedPage = mapper.readValue(json, new TypeReference<Page<Dummy>>() {})
        deserializedPage == page
    }

    @EqualsAndHashCode
    @ToString
    static class Dummy {
        String propertyOne
        Long propertyTwo
        BigDecimal propertyThree
    }
}

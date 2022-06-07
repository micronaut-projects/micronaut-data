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
package io.micronaut.data.runtime.http

import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.runtime.config.DataConfiguration
import io.micronaut.http.HttpRequest
import spock.lang.Specification
import spock.lang.Unroll

class PageableRequestArgumentBinderSpec extends Specification {

    @Unroll
    void 'test bind size #size and page #page'() {
        given:
        PageableRequestArgumentBinder binder = new PageableRequestArgumentBinder(new DataConfiguration.PageableConfiguration())
        def get = HttpRequest.GET('/')
        get.parameters.add("size", size)
        get.parameters.add("page", page)
        Pageable p = binder.bind(ConversionContext.of(Pageable), get).get()

        expect:
        p.size == pageSize
        p.number == pageNumber

        where:
        size   | page | pageSize | pageNumber
        "20"   | "1"  | 20       | 1
        "120"  | "10" | 100      | 10  // exceeds max
        "-1"   | "10" | 100      | 10  // negative
        "-1"   | "0"  | 100      | 0  // negative
        "junk" | "0"  | 100      | 0  // can't be parsed
    }

    @Unroll
    void 'test bind sort #sort'() {
        given:
        PageableRequestArgumentBinder binder = new PageableRequestArgumentBinder(new DataConfiguration.PageableConfiguration())
        def get = HttpRequest.GET('/')
        get.parameters.add("sort", sort)

        Pageable p = binder.bind(ConversionContext.of(Pageable), get).get()

        expect:
        p.sorted
        p.orderBy == orderBy.getOrderBy()

        where:
        sort                 | orderBy
        ['name']             | Sort.of([Sort.Order.asc("name")])
        ['name', 'age']      | Sort.of([Sort.Order.asc("name"), Sort.Order.asc("age")])
        ['name,desc', 'age'] | Sort.of([Sort.Order.desc("name"), Sort.Order.asc("age")])
        ['name,DESC', 'age'] | Sort.of([Sort.Order.desc("name"), Sort.Order.asc("age")])
    }

    @Unroll
    void 'test bind size #size and page #page with custom configuration'() {
        given:
        def configuration = new DataConfiguration.PageableConfiguration()
        configuration.defaultPageSize = 40
        configuration.maxPageSize = 200
        configuration.sizeParameterName = "perPage"
        configuration.pageParameterName = "myPage"
        PageableRequestArgumentBinder binder = new PageableRequestArgumentBinder(configuration)
        def get = HttpRequest.GET('/')
        get.parameters.add("perPage", size)
        get.parameters.add("myPage", page)
        Pageable p = binder.bind(ConversionContext.of(Pageable), get).get()

        expect:
        p.size == pageSize
        p.number == pageNumber

        where:
        size   | page | pageSize | pageNumber
        "20"   | "1"  | 20       | 1
        "230"  | "12" | 200      | 12  // exceeds max => uses max
        "-1"   | "10" | 40       | 10  // negative    => uses default != max
        "-1"   | "0"  | 40       | 0   // negative    => uses default != max
        "junk" | "0"  | 40       | 0   // can't be parsed
    }

    @Unroll
    void 'test page #page is or is not shifted with custom configuration for startFromPageOne #startFromPageOne'() {
        given:
        def configuration = new DataConfiguration.PageableConfiguration()
        configuration.startFromPageOne = startFromPageOne
        PageableRequestArgumentBinder binder = new PageableRequestArgumentBinder(configuration)
        def get = HttpRequest.GET('/')
        get.parameters.add("page", page)
        Pageable p = binder.bind(ConversionContext.of(Pageable), get).get()

        expect:
        p.number == pageNumber

        where:
        page | pageNumber | startFromPageOne
        "1"  | 0          | true // first page is shifted to 0
        "5"  | 4          | true // fifth page is shifted to 4
        "0"  | 0          | true // 0 page is still 0
    }
}

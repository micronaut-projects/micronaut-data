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
        configuration.defaultPageSize=40
        configuration.maxPageSize=200
        configuration.sizeParameterName="perPage"
        configuration.pageParameterName="myPage"
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
}

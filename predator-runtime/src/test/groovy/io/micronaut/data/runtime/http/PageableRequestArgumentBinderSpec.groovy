package io.micronaut.data.runtime.http

import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.runtime.config.PredatorConfiguration
import io.micronaut.http.HttpRequest
import spock.lang.Specification
import spock.lang.Unroll

class PageableRequestArgumentBinderSpec extends Specification {

    @Unroll
    void 'test bind size #size and page #page'() {
        given:
        PageableRequestArgumentBinder binder = new PageableRequestArgumentBinder(new PredatorConfiguration.PageableConfiguration())
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
}

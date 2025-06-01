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

import groovy.transform.Memoized
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.data.runtime.intercept.DataInterceptorResolver
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class H2RepositoryScopeSpec extends Specification {

    @Inject
    @Shared
    BeanContext beanContext

    void "test default repository scope is prototype"() {
        when:
            def instance1 = beanContext.getBean(H2BookRepository)
            def instance2 = beanContext.getBean(H2BookRepository)
        then:
            !instance1.is(instance2)
    }

    void "test explicit singleton repository scope is honored"() {
        when:
            def instance1 = beanContext.getBean(H2BookDtoRepository)
            def instance2 = beanContext.getBean(H2BookDtoRepository)
        then:
            instance1.is(instance2)
    }

    void "test no memory leak 1"() {
        when:
            def dataInterceptor = getDataInterceptor()
            def instance = beanContext.getBean(H2BookRepository)
        then:
            for (i in (1..30000)) {
                instance.deleteAll()
                dataInterceptor.@interceptors.size() < 10000
            }
    }

    void "test no memory leak 2"() {
        when:
            def dataInterceptor = getDataInterceptor()
            def instance = bookRepository
        then:
            for (i in (1..30000)) {
                instance.deleteAll()
                dataInterceptor.@interceptors.size() < 10000
            }
    }

    void "test no memory leak 3"() {
        when:
            def dataInterceptor = getDataInterceptor()
            def myService = beanContext.getBean(MyPrototypeService)
        then:
            myService.bookRepository.deleteAll()
            for (i in (1..30000)) {
                dataInterceptor.@interceptors.size() < 10000
            }
    }

    @Memoized
    private DataInterceptorResolver getDataInterceptor() {
        return beanContext.getBean(DataInterceptorResolver)
    }

    @Memoized
    H2BookRepository getBookRepository() {
        beanContext.getBean(H2BookRepository)
    }

    @Prototype
    static class MyPrototypeService {

        final H2BookRepository bookRepository

        MyPrototypeService(H2BookRepository bookRepository) {
            this.bookRepository = bookRepository
        }
    }

}

/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.CountInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.async.CountAsyncInterceptor
import io.micronaut.data.intercept.reactive.CountReactiveInterceptor
import spock.lang.Unroll

class CountSpec extends AbstractDataSpec {

    @Unroll
    void "test count method variations for method #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.model.entities.*;
import java.util.concurrent.*;
import io.reactivex.*;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    $returnType $method($arguments);
}
"""
        )

        def executableMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def query = executableMethod.stringValue(Query).orElse(null)
        def ann = executableMethod
                .synthesize(DataMethod)

        expect:
        ann.resultType() == resultType
        ann.interceptor() == interceptor
        query.startsWith(expectedQuery)

        where:
        method                    | returnType              | arguments     | interceptor              | resultType | expectedQuery
        "count"                   | "Long"                  | "String name" | CountInterceptor         | Long       | "SELECT COUNT(person_)"
        "countByName"             | "Long"                  | "String name" | CountInterceptor         | Long       | "SELECT COUNT(person_)"
        "countDistinct"           | "Long"                  | "String name" | CountInterceptor         | Long       | "SELECT COUNT(person_)"
        "countDistinctByName"     | "Long"                  | "String name" | CountInterceptor         | Long       | "SELECT COUNT(person_)"
        "countDistinctName"       | "Long"                  | "String name" | CountInterceptor         | Long       | "SELECT COUNT(DISTINCT(person_.name))"
        "countDistinctNameByName" | "Long"                  | "String name" | CountInterceptor         | Long       | "SELECT COUNT(DISTINCT(person_.name))"
        "countDistinctNameByName" | "Single<Long>"          | "String name" | CountReactiveInterceptor | Long       | "SELECT COUNT(DISTINCT(person_.name))"
        "countDistinctName"       | "CompletionStage<Long>" | "String name" | CountAsyncInterceptor    | Long       | "SELECT COUNT(DISTINCT(person_.name))"
    }
}

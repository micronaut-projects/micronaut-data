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
package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.DeleteAllInterceptor
import io.micronaut.data.intercept.DeleteOneInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.entities.Person
import spock.lang.Unroll

class DeleteBySpec extends AbstractDataMethodSpec {

    @Unroll
    void "test equals method #method"() {
        given:
        def executableMethod = buildMethod("Person", returnType, method, arguments)
        Class targetInterceptor = executableMethod
                .classValue(DataMethod, "interceptor").orElse(null)
        String query = executableMethod.stringValue(Query).orElse(null)

        expect:
        targetInterceptor == interceptor
        query == expectedQuery

        where:
        returnType | method                              | arguments       | interceptor          | expectedQuery
        "void"     | "deleteByName"                      | "String name"   | DeleteAllInterceptor | "DELETE $Person.name  AS person_ WHERE (person_.name = :p1)"
        "int"      | "deleteByName"                      | "String name"   | DeleteAllInterceptor | "DELETE $Person.name  AS person_ WHERE (person_.name = :p1)"
        "Long"     | "deleteByName"                      | "String name"   | DeleteAllInterceptor | "DELETE $Person.name  AS person_ WHERE (person_.name = :p1)"
        "Number"   | "deleteByName"                      | "String name"   | DeleteAllInterceptor | "DELETE $Person.name  AS person_ WHERE (person_.name = :p1)"
        "Number"   | "deleteByNameIsEmptyOrNameIsNull"   | "String name"   | DeleteAllInterceptor | "DELETE io.micronaut.data.model.entities.Person  AS person_ WHERE ((person_.name IS NULL OR person_.name = ''  OR person_.name IS NULL ))"
        "Number"   | "deleteByNameIsEmptyOrNameIsNull"   | "Person person" | DeleteOneInterceptor | "DELETE io.micronaut.data.model.entities.Person  AS person_ WHERE ((person_.name IS NULL OR person_.name = ''  OR person_.name IS NULL ))"
        "Number"   | "deleteByNameIsEmptyOrNameIsNull"   | ""              | DeleteAllInterceptor | "DELETE io.micronaut.data.model.entities.Person  AS person_ WHERE ((person_.name IS NULL OR person_.name = ''  OR person_.name IS NULL ))"
    }
}

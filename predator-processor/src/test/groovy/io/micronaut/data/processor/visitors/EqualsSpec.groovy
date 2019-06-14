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
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import spock.lang.Unroll

class EqualsSpec extends AbstractPredatorMethodSpec {

    @Unroll
    void "test equals method #method"() {
        given:
        def executableMethod = buildMethod(returnType, method, arguments)
        Class targetInterceptor = executableMethod
                .classValue(PredatorMethod, "interceptor").orElse(null)
        String query = executableMethod.stringValue(Query).orElse(null)

        expect:
        targetInterceptor == interceptor
        query == expectedQuery

        where:
        alias   | method                 | returnType | arguments     | interceptor        | expectedQuery
        alias() | "findByNameIgnoreCase" | "Person"   | "String name" | FindOneInterceptor | "SELECT $alias FROM $Person.name AS $alias WHERE (lower(${alias}.name) = lower(:p1))"
        alias() | "findByName"           | "Person"   | "String name" | FindOneInterceptor | "SELECT $alias FROM $Person.name AS $alias WHERE (${alias}.name = :p1)"
        alias() | "findByNameEqual"      | "Person"   | "String name" | FindOneInterceptor | "SELECT $alias FROM $Person.name AS $alias WHERE (${alias}.name = :p1)"
        alias() | "findByNameEquals"     | "Person"   | "String name" | FindOneInterceptor | "SELECT $alias FROM $Person.name AS $alias WHERE (${alias}.name = :p1)"
        alias() | "findByNameNotEquals"  | "Person"   | "String name" | FindOneInterceptor | "SELECT $alias FROM $Person.name AS $alias WHERE (${alias}.name != :p1)"
        alias() | "findByNameNotEqual"   | "Person"   | "String name" | FindOneInterceptor | "SELECT $alias FROM $Person.name AS $alias WHERE (${alias}.name != :p1)"
    }

    private String alias() {
        new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))
    }
}

package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.entities.Person
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
        method                 | returnType | arguments     | interceptor        | expectedQuery
        "findByNameIgnoreCase" | "Person"   | "String name" | FindOneInterceptor | "SELECT person FROM $Person.name AS person WHERE (lower(person.name) = lower(:p1))"
        "findByName"           | "Person"   | "String name" | FindOneInterceptor | "SELECT person FROM $Person.name AS person WHERE (person.name = :p1)"
        "findByNameEqual"      | "Person"   | "String name" | FindOneInterceptor | "SELECT person FROM $Person.name AS person WHERE (person.name = :p1)"
        "findByNameEquals"     | "Person"   | "String name" | FindOneInterceptor | "SELECT person FROM $Person.name AS person WHERE (person.name = :p1)"
        "findByNameNotEquals"  | "Person"   | "String name" | FindOneInterceptor | "SELECT person FROM $Person.name AS person WHERE (person.name != :p1)"
        "findByNameNotEqual"   | "Person"   | "String name" | FindOneInterceptor | "SELECT person FROM $Person.name AS person WHERE (person.name != :p1)"
    }
}

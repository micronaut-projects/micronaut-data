package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.DeleteAllInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
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
        returnType | method         | arguments     | interceptor          | expectedQuery
        "void"     | "deleteByName" | "String name" | DeleteAllInterceptor | "DELETE $Person.name  AS person_ WHERE (person_.name = :p1)"
        "int"      | "deleteByName" | "String name" | DeleteAllInterceptor | "DELETE $Person.name  AS person_ WHERE (person_.name = :p1)"
        "Long"     | "deleteByName" | "String name" | DeleteAllInterceptor | "DELETE $Person.name  AS person_ WHERE (person_.name = :p1)"
        "Number"   | "deleteByName" | "String name" | DeleteAllInterceptor | "DELETE $Person.name  AS person_ WHERE (person_.name = :p1)"
    }
}
